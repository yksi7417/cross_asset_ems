/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.crossasset.ems.aaa.AaaService;
import io.crossasset.ems.aaa.CredentialKind;
import io.crossasset.ems.aaa.InMemoryAaaEventLog;
import io.crossasset.ems.aaa.InMemoryAaaService;
import io.crossasset.ems.aaa.LogonCredentials;
import io.crossasset.ems.aaa.LogonOutcome;
import io.crossasset.ems.api.SubscriptionRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Real-socket round-trip for the WebSocket event stream (task 18.1): RFC 6455 handshake with
 * accept-key verification, replay-then-live delivery as text frames, cursor resume on reconnect
 * ({@code from=lastSeq+1}), session auth, and unsubscribe on close. The test client is a raw
 * socket: handshake + unmasked-frame reader + masked close frame, no WS library.
 */
class WsEventStreamServerTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final SubscriptionRegistry registry = new SubscriptionRegistry();
  private AaaService aaa;
  private WsEventStreamServer server;
  private long sessionId;

  @BeforeEach
  void setUp() throws Exception {
    InMemoryAaaService aaaService = new InMemoryAaaService(new InMemoryAaaEventLog());
    aaaService.registerCredential("tok-ws", "firm-a", "desk-1", "trader-ws", Set.of());
    LogonOutcome outcome = aaaService.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "tok-ws"));
    sessionId = ((LogonOutcome.Accepted) outcome).session().sessionId();
    aaa = aaaService;
    server = new WsEventStreamServer(aaa, registry, 0);
    server.start();
  }

  @AfterEach
  void tearDown() {
    server.close();
  }

  // ── Minimal raw-socket WS client ─────────────────────────────────────────────

  private static final class WsClient implements AutoCloseable {
    final Socket socket;
    final InputStream in;
    final OutputStream out;
    final String statusLine;
    final String acceptHeader;

    WsClient(int port, String pathAndQuery) throws IOException {
      socket = new Socket("127.0.0.1", port);
      socket.setSoTimeout(7_000);
      in = socket.getInputStream();
      out = socket.getOutputStream();
      out.write(
          ("GET "
                  + pathAndQuery
                  + " HTTP/1.1\r\nHost: 127.0.0.1\r\nUpgrade: websocket\r\n"
                  + "Connection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                  + "Sec-WebSocket-Version: 13\r\n\r\n")
              .getBytes(StandardCharsets.UTF_8));
      out.flush();
      StringBuilder head = new StringBuilder();
      int c;
      while ((c = in.read()) >= 0) {
        head.append((char) c);
        if (head.length() >= 4 && head.substring(head.length() - 4).equals("\r\n\r\n")) {
          break;
        }
      }
      String[] lines = head.toString().split("\r\n");
      statusLine = lines[0];
      String accept = "";
      for (String line : lines) {
        if (line.toLowerCase(java.util.Locale.ROOT).startsWith("sec-websocket-accept:")) {
          accept = line.substring(line.indexOf(':') + 1).trim();
        }
      }
      acceptHeader = accept;
    }

    /** Read one server frame; returns the text payload (server frames are unmasked). */
    String readTextFrame() throws IOException {
      int b0 = in.read();
      int b1 = in.read();
      if (b0 < 0 || b1 < 0) {
        throw new IOException("stream closed");
      }
      long length = b1 & 0x7F;
      if (length == 126) {
        length = ((long) in.read() << 8) | in.read();
      } else if (length == 127) {
        length = 0;
        for (int i = 0; i < 8; i++) {
          length = (length << 8) | in.read();
        }
      }
      byte[] payload = in.readNBytes((int) length);
      return new String(payload, StandardCharsets.UTF_8);
    }

    /** Send a masked close frame (clients must mask per RFC 6455). */
    void sendClose() throws IOException {
      out.write(new byte[] {(byte) 0x88, (byte) 0x80, 0x12, 0x34, 0x56, 0x78});
      out.flush();
    }

    @Override
    public void close() throws IOException {
      socket.close();
    }
  }

  private String wsPath(String topic, long from) {
    return "/ws/events?session=" + sessionId + "&topic=" + topic + "&from=" + from;
  }

  // ── Tests ────────────────────────────────────────────────────────────────────

  @Test
  void handshake_returns101WithCorrectAcceptKey() throws Exception {
    try (WsClient client = new WsClient(server.port(), wsPath("md", 1))) {
      assertThat(client.statusLine).contains("101");
      String expected =
          Base64.getEncoder()
              .encodeToString(
                  MessageDigest.getInstance("SHA-1")
                      .digest(
                          ("dGhlIHNhbXBsZSBub25jZQ==258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                              .getBytes(StandardCharsets.UTF_8)));
      assertThat(client.acceptHeader).isEqualTo(expected);
    }
  }

  @Test
  void replayThenLive_arriveAsTextFrames() throws Exception {
    registry.publish("blotter.orders", "OrderRow", "ORD-1", "{\"orderId\":\"ORD-1\"}");
    registry.publish("blotter.orders", "OrderRow", "ORD-2", "{\"orderId\":\"ORD-2\"}");

    try (WsClient client = new WsClient(server.port(), wsPath("blotter.orders", 1))) {
      JsonNode first = mapper.readTree(client.readTextFrame());
      JsonNode second = mapper.readTree(client.readTextFrame());
      assertThat(first.get("seq").asLong()).isEqualTo(1);
      assertThat(first.get("refId").asText()).isEqualTo("ORD-1");
      assertThat(second.get("seq").asLong()).isEqualTo(2);

      registry.publish("blotter.orders", "OrderRow", "ORD-3", "{\"orderId\":\"ORD-3\"}");
      JsonNode live = mapper.readTree(client.readTextFrame());
      assertThat(live.get("seq").asLong()).isEqualTo(3);
      assertThat(live.get("type").asText()).isEqualTo("OrderRow");
      assertThat(live.get("payload").asText()).isEqualTo("{\"orderId\":\"ORD-3\"}");
    }
  }

  @Test
  void idleStream_emitsHeartbeatFrame() throws Exception {
    try (WsClient client = new WsClient(server.port(), wsPath("md", 1))) {
      JsonNode heartbeat = mapper.readTree(client.readTextFrame());
      assertThat(heartbeat.get("topic").asText()).isEqualTo("md");
      assertThat(heartbeat.get("seq").asLong()).isEqualTo(0);
      assertThat(heartbeat.get("type").asText()).isEqualTo("heartbeat");
    }
  }

  @Test
  void reconnectWithCursor_resumesAfterLastDelivered() throws Exception {
    registry.publish("md", "MdTick", "F1", "{}");
    registry.publish("md", "MdTick", "F2", "{}");
    registry.publish("md", "MdTick", "F3", "{}");

    try (WsClient client = new WsClient(server.port(), wsPath("md", 3))) {
      JsonNode frame = mapper.readTree(client.readTextFrame());
      assertThat(frame.get("seq").asLong()).isEqualTo(3);
      assertThat(frame.get("refId").asText()).isEqualTo("F3");
    }
  }

  @Test
  void invalidSession_rejectedWith401BeforeUpgrade() throws Exception {
    try (WsClient client =
        new WsClient(server.port(), "/ws/events?session=999999&topic=md&from=1")) {
      assertThat(client.statusLine).contains("401");
    }
  }

  @Test
  void missingTopic_rejectedWith400() throws Exception {
    try (WsClient client = new WsClient(server.port(), "/ws/events?session=" + sessionId)) {
      assertThat(client.statusLine).contains("400");
    }
  }

  @Test
  void closeFrame_unsubscribesAndAcks() throws Exception {
    try (WsClient client = new WsClient(server.port(), wsPath("md", 1))) {
      awaitSubscriptions(1);
      client.sendClose();
      int b0 = client.in.read();
      assertThat(b0 & 0x0F).as("close ack opcode").isEqualTo(0x8);
      awaitSubscriptions(0);
    }
  }

  @Test
  void socketDrop_unsubscribes() throws Exception {
    WsClient client = new WsClient(server.port(), wsPath("md", 1));
    awaitSubscriptions(1);
    client.close();
    awaitSubscriptions(0);
  }

  private void awaitSubscriptions(int expected) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 5_000;
    while (System.currentTimeMillis() < deadline) {
      if (registry.subscriptionsForSession(sessionId).size() == expected) {
        return;
      }
      Thread.sleep(10);
    }
    List<String> topics = new ArrayList<>(registry.subscriptionsForSession(sessionId).values());
    assertThat(topics).as("live subscriptions for session").hasSize(expected);
  }
}
