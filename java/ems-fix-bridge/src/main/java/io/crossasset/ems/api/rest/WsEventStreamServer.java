/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.crossasset.ems.aaa.AaaService;
import io.crossasset.ems.api.ApiEvent;
import io.crossasset.ems.api.SubscriptionRegistry;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebSocket edge for the resumable subscription stream (task 18.1, completing the 8.10 edge): the
 * Perspective desktop holds one socket per topic and receives each {@link ApiEvent} as a JSON text
 * frame — row deltas pushed on publish, never polled, never a full refresh. RFC 6455 server side,
 * dependency-free (JDK sockets + SHA-1), one virtual thread per connection.
 *
 * <p>Resume contract is identical to {@code GET /api/v1/events}: the client holds its last
 * delivered {@code seq} and reconnects with {@code from=lastSeq+1}; the server holds no per-client
 * state beyond the live socket (arch-api-first § Resume). Handshake: {@code GET
 * /ws/events?session=&topic=&from=} — the session comes from AAA logon over REST; browsers cannot
 * set WS headers, hence the query parameter.
 */
public final class WsEventStreamServer implements AutoCloseable {

  private static final String WS_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
  private static final long HEARTBEAT_MS = 2_000L;

  private final AaaService aaa;
  private final SubscriptionRegistry subscriptions;
  private final ServerSocket serverSocket;
  private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean running = new AtomicBoolean();
  private final ObjectMapper mapper = new ObjectMapper();

  public WsEventStreamServer(AaaService aaa, SubscriptionRegistry subscriptions, int port)
      throws IOException {
    this.aaa = Objects.requireNonNull(aaa, "aaa");
    this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
    this.serverSocket = new ServerSocket(port);
  }

  public void start() {
    if (!running.compareAndSet(false, true)) {
      return;
    }
    Thread.ofVirtual().name("ws-accept").start(this::acceptLoop);
  }

  /** The bound port (useful when constructed with port 0). */
  public int port() {
    return serverSocket.getLocalPort();
  }

  @Override
  public void close() {
    running.set(false);
    try {
      serverSocket.close();
    } catch (IOException e) {
      // Shutdown path — nothing to surface.
    }
    for (Socket client : clients) {
      try {
        client.close();
      } catch (IOException e) {
        // Shutdown path — nothing to surface.
      }
    }
  }

  private void acceptLoop() {
    while (running.get()) {
      try {
        Socket socket = serverSocket.accept();
        clients.add(socket);
        Thread.ofVirtual().name("ws-conn").start(() -> serve(socket));
      } catch (IOException e) {
        return; // Server socket closed.
      }
    }
  }

  // ── One connection ───────────────────────────────────────────────────────────

  private void serve(Socket socket) {
    String subscriptionId = null;
    AtomicBoolean streaming = new AtomicBoolean(false);
    Thread heartbeat = null;
    try (socket) {
      InputStream in = new BufferedInputStream(socket.getInputStream());
      OutputStream out = socket.getOutputStream();

      Request request = readRequest(in);
      if (request == null
          || !"/ws/events".equals(request.path)
          || request.headers.get("sec-websocket-key") == null) {
        writeHttp(out, "400 Bad Request", "expected GET /ws/events with a WebSocket upgrade");
        return;
      }
      String topic = request.query.get("topic");
      if (topic == null || topic.isBlank()) {
        writeHttp(out, "400 Bad Request", "query param 'topic' is required");
        return;
      }
      long sessionId;
      try {
        sessionId = Long.parseLong(request.query.getOrDefault("session", ""));
      } catch (NumberFormatException e) {
        writeHttp(out, "401 Unauthorized", "query param 'session' is required");
        return;
      }
      if (aaa.sessionInfo(sessionId).isEmpty()) {
        writeHttp(out, "401 Unauthorized", "session not found or expired");
        return;
      }
      long from = parseLongOrDefault(request.query.get("from"), 1L);

      writeHandshake(out, request.headers.get("sec-websocket-key"));

      FrameSink sink = new FrameSink(out, renderHeartbeat(topic));
      subscriptionId =
          subscriptions.subscribe(
              sessionId, topic, from, (sid, subId, event) -> sink.send(event.seq(), render(event)));
      streaming.set(true);
      heartbeat = startHeartbeatLoop(streaming, sink);

      readLoop(in, out);
    } catch (IOException e) {
      // Connection dropped — the unsubscribe in finally is the cleanup.
    } finally {
      streaming.set(false);
      if (heartbeat != null) {
        heartbeat.interrupt();
      }
      if (subscriptionId != null) {
        subscriptions.unsubscribe(subscriptionId);
      }
      clients.remove(socket);
    }
  }

  /** Client frames: close → ack + exit, ping → pong, everything else ignored. */
  private void readLoop(InputStream in, OutputStream out) throws IOException {
    while (true) {
      int b0 = in.read();
      if (b0 < 0) {
        return;
      }
      int opcode = b0 & 0x0F;
      int b1 = in.read();
      if (b1 < 0) {
        return;
      }
      boolean masked = (b1 & 0x80) != 0;
      long length = b1 & 0x7F;
      if (length == 126) {
        length = ((long) in.read() << 8) | in.read();
      } else if (length == 127) {
        length = 0;
        for (int i = 0; i < 8; i++) {
          length = (length << 8) | in.read();
        }
      }
      byte[] mask = new byte[4];
      if (masked) {
        in.readNBytes(mask, 0, 4);
      }
      byte[] payload = in.readNBytes((int) length);
      if (masked) {
        for (int i = 0; i < payload.length; i++) {
          payload[i] ^= mask[i % 4];
        }
      }
      if (opcode == 0x8) {
        synchronized (out) {
          out.write(new byte[] {(byte) 0x88, 0});
          out.flush();
        }
        return;
      }
      if (opcode == 0x9) {
        synchronized (out) {
          out.write(frame((byte) 0xA, payload));
          out.flush();
        }
      }
      // Text/binary from the client is not part of this protocol — subscriptions are per-URL.
    }
  }

  // ── Wire helpers ─────────────────────────────────────────────────────────────

  private record Request(String path, Map<String, String> query, Map<String, String> headers) {}

  /** Minimal HTTP request-head parser (request line + headers, no body on GET). */
  private static Request readRequest(InputStream in) throws IOException {
    String head = readHead(in);
    if (head == null) {
      return null;
    }
    String[] lines = head.split("\r\n");
    String[] requestLine = lines[0].split(" ");
    if (requestLine.length < 2 || !"GET".equals(requestLine[0])) {
      return null;
    }
    String uri = requestLine[1];
    String path = uri;
    Map<String, String> query = new HashMap<>();
    int q = uri.indexOf('?');
    if (q >= 0) {
      path = uri.substring(0, q);
      for (String pair : uri.substring(q + 1).split("&")) {
        int eq = pair.indexOf('=');
        if (eq > 0) {
          query.put(
              java.net.URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
              java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
      }
    }
    Map<String, String> headers = new HashMap<>();
    for (int i = 1; i < lines.length; i++) {
      int colon = lines[i].indexOf(':');
      if (colon > 0) {
        headers.put(
            lines[i].substring(0, colon).toLowerCase(java.util.Locale.ROOT),
            lines[i].substring(colon + 1).trim());
      }
    }
    return new Request(path, query, headers);
  }

  private static String readHead(InputStream in) throws IOException {
    StringBuilder head = new StringBuilder();
    int prev3 = -1;
    int prev2 = -1;
    int prev = -1;
    int c;
    while ((c = in.read()) >= 0) {
      head.append((char) c);
      if (prev3 == '\r' && prev2 == '\n' && prev == '\r' && c == '\n') {
        return head.substring(0, head.length() - 4);
      }
      prev3 = prev2;
      prev2 = prev;
      prev = c;
      if (head.length() > 16_384) {
        return null;
      }
    }
    return null;
  }

  private static void writeHandshake(OutputStream out, String key) throws IOException {
    String accept;
    try {
      accept =
          Base64.getEncoder()
              .encodeToString(
                  MessageDigest.getInstance("SHA-1")
                      .digest((key + WS_MAGIC).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("JVM without SHA-1", e);
    }
    out.write(
        ("HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: "
                + accept
                + "\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    out.flush();
  }

  private static void writeHttp(OutputStream out, String status, String message)
      throws IOException {
    byte[] body = message.getBytes(StandardCharsets.UTF_8);
    out.write(
        ("HTTP/1.1 "
                + status
                + "\r\nContent-Type: text/plain\r\nContent-Length: "
                + body.length
                + "\r\nConnection: close\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    out.write(body);
    out.flush();
  }

  /** One server→client frame (FIN set, unmasked, as RFC 6455 requires of servers). */
  private static byte[] frame(byte opcode, byte[] payload) {
    int headerLen = payload.length < 126 ? 2 : payload.length < 65_536 ? 4 : 10;
    byte[] frame = new byte[headerLen + payload.length];
    frame[0] = (byte) (0x80 | opcode);
    if (payload.length < 126) {
      frame[1] = (byte) payload.length;
    } else if (payload.length < 65_536) {
      frame[1] = 126;
      frame[2] = (byte) (payload.length >> 8);
      frame[3] = (byte) payload.length;
    } else {
      frame[1] = 127;
      long len = payload.length;
      for (int i = 0; i < 8; i++) {
        frame[9 - i] = (byte) (len >> (8 * i));
      }
    }
    System.arraycopy(payload, 0, frame, headerLen, payload.length);
    return frame;
  }

  private String render(ApiEvent event) {
    ObjectNode node = mapper.createObjectNode();
    node.put("topic", event.topic());
    node.put("seq", event.seq());
    node.put("type", event.type());
    node.put("refId", event.refId());
    node.put("payload", event.payload());
    return node.toString();
  }

  private String renderHeartbeat(String topic) {
    ObjectNode node = mapper.createObjectNode();
    node.put("topic", topic);
    node.put("seq", 0);
    node.put("type", "heartbeat");
    node.put("refId", "");
    node.put("payload", "");
    return node.toString();
  }

  private Thread startHeartbeatLoop(AtomicBoolean streaming, FrameSink sink) {
    return Thread.ofVirtual()
        .name("ws-heartbeat")
        .start(
            () -> {
              while (streaming.get()) {
                try {
                  Thread.sleep(HEARTBEAT_MS);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  return;
                }
                if (streaming.get()) {
                  sink.sendHeartbeat();
                }
              }
            });
  }

  private static long parseLongOrDefault(String value, long fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  /**
   * Serialized frame writer: replay (subscribe thread) and live publishes (publisher threads) both
   * funnel here; {@code lastSeqSent} drops the duplicate delivery possible at the attach boundary.
   */
  private static final class FrameSink {
    private final OutputStream out;
    private final String heartbeat;
    private long lastSeqSent;

    FrameSink(OutputStream out, String heartbeat) {
      this.out = out;
      this.heartbeat = heartbeat;
    }

    synchronized void send(long seq, String json) {
      if (seq <= lastSeqSent) {
        return;
      }
      lastSeqSent = seq;
      try {
        out.write(frame((byte) 0x1, json.getBytes(StandardCharsets.UTF_8)));
        out.flush();
      } catch (IOException e) {
        // Connection dropped; the read loop notices and unsubscribes.
      }
    }

    synchronized void sendHeartbeat() {
      try {
        out.write(frame((byte) 0x1, heartbeat.getBytes(StandardCharsets.UTF_8)));
        out.flush();
      } catch (IOException e) {
        // Connection dropped; the read loop notices and unsubscribes.
      }
    }
  }
}
