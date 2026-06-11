/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.crossasset.ems.aaa.InMemoryAaaEventLog;
import io.crossasset.ems.aaa.InMemoryAaaService;
import io.crossasset.ems.api.ApiSurface;
import io.crossasset.ems.api.SubscriptionRegistry;
import io.crossasset.ems.instrument.AssetClass;
import io.crossasset.ems.instrument.CurrencyCode;
import io.crossasset.ems.instrument.Fungibility;
import io.crossasset.ems.instrument.InMemorySecurityMasterService;
import io.crossasset.ems.instrument.InstrumentCore;
import io.crossasset.ems.instrument.InstrumentType;
import io.crossasset.ems.instrument.InstrumentVersioned;
import io.crossasset.ems.instrument.LifecycleStatus;
import io.crossasset.ems.instrument.SecurityMasterEvent;
import io.crossasset.ems.instrument.SecurityMasterSnapshot;
import io.crossasset.ems.instrument.SettlementConvention;
import io.crossasset.ems.oms.InMemoryRouteManager;
import io.crossasset.ems.oms.InMemoryStagedOrderManager;
import io.crossasset.ems.transport.session.SequenceRecoveryService;
import io.crossasset.ems.validator.LayeredValidatorPipeline;
import io.crossasset.ems.validator.ValidatorPipeline;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Real-HTTP round-trip for the REST edge binding (task 8.10): logon → stage over the wire →
 * resumable event fetch with Last-Event-ID-style cursors. JDK HttpServer + HttpClient only.
 */
class RestHttpServerTest {

  private static final String FIGI = "BBG000BLNNH6";

  private final ObjectMapper mapper = new ObjectMapper();
  private final HttpClient client = HttpClient.newHttpClient();
  private RestHttpServer server;
  private String base;

  @BeforeEach
  void setUp() throws Exception {
    InMemoryAaaService aaa =
        new InMemoryAaaService(
            new InMemoryAaaEventLog(), null, new SequenceRecoveryService(() -> 0L));
    InMemorySecurityMasterService secMaster = new InMemorySecurityMasterService();
    ValidatorPipeline pipeline = new LayeredValidatorPipeline(aaa, secMaster, null);
    InMemoryStagedOrderManager som = new InMemoryStagedOrderManager(pipeline);
    SubscriptionRegistry subscriptions = new SubscriptionRegistry();
    ApiSurface api =
        new ApiSurface(
            aaa, som, new InMemoryRouteManager(som), subscriptions, (sid, subId, event) -> {});
    aaa.registerCredential("tok-ui", "firm-a", "desk-1", "trader-ui", Set.of());

    InstrumentCore core =
        new InstrumentCore(
            FIGI,
            "IID-1",
            null,
            null,
            AssetClass.EQUITY,
            InstrumentType.COMMON_STOCK,
            "Test Stock",
            "Test Inc.",
            null,
            CurrencyCode.USD,
            "US",
            null,
            Fungibility.FUNGIBLE,
            SettlementConvention.T_PLUS_2,
            0,
            LifecycleStatus.ACTIVE,
            1_000_000L,
            Long.MAX_VALUE,
            1L,
            null,
            1_000_000L,
            1_000_000L);
    secMaster.publish(
        SecurityMasterSnapshot.EMPTY.apply(
            new SecurityMasterEvent.InstrumentCreated(new InstrumentVersioned(core, null), 1L)));

    server = new RestHttpServer(new RestEdgeBinding(aaa, api, subscriptions), 0);
    server.start();
    base = "http://localhost:" + server.port();
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  @Test
  void logon_stage_fetchEvents_resumeCursor() throws Exception {
    // 1. Logon over the wire.
    JsonNode logon = postJson("/api/v1/logon", null, "{\"token\":\"tok-ui\"}");
    long sessionId = logon.path("sessionId").asLong();
    assertThat(sessionId).isPositive();

    // 2. Stage a batch of two through REST.
    String stageBody =
        """
        {"requestId":"ui-1","sessionSeq":1,"items":[
          {"clOrdId":"CL-UI-1","figi":"%s","side":1,"qty":100,"account":"acc-1"},
          {"clOrdId":"CL-UI-2","figi":"%s","side":2,"qty":200,"account":"acc-1","price":1012500}
        ]}
        """
            .formatted(FIGI, FIGI);
    JsonNode stage = postJson("/api/v1/stage_orders", sessionId, stageBody);
    assertThat(stage.path("summary").path("ok").asInt()).isEqualTo(2);
    String orderId = stage.path("results").get(0).path("refId").asText();
    assertThat(orderId).startsWith("EMS-ORD-");

    // 3. Fetch the blotter stream from seq 1 — both staged events.
    JsonNode events = getJson("/api/v1/events?topic=orders&from=1");
    assertThat(events.path("events")).hasSize(2);
    long nextFrom = events.path("nextFrom").asLong();
    assertThat(nextFrom).isEqualTo(3);

    // 4. Resume from the cursor — nothing new yet.
    JsonNode resume = getJson("/api/v1/events?topic=orders&from=" + nextFrom);
    assertThat(resume.path("events")).isEmpty();

    // 5. New activity lands after the cursor; the next fetch picks up exactly the new event.
    postJson(
        "/api/v1/cancel_orders",
        sessionId,
        "{\"requestId\":\"ui-2\",\"sessionSeq\":2,\"items\":[{\"orderId\":\"" + orderId + "\"}]}");
    JsonNode after = getJson("/api/v1/events?topic=orders&from=" + nextFrom);
    assertThat(after.path("events")).hasSize(1);
    assertThat(after.path("events").get(0).path("type").asText()).isEqualTo("OrderCanceled");
  }

  @Test
  void logon_badToken_returns401() throws Exception {
    HttpResponse<String> response =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/api/v1/logon"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"token\":\"nope\"}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Test
  void unknownOperation_returns400() throws Exception {
    JsonNode logon = postJson("/api/v1/logon", null, "{\"token\":\"tok-ui\"}");
    long sessionId = logon.path("sessionId").asLong();
    HttpResponse<String> response =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/api/v1/explode"))
                .header("X-EMS-Session", Long.toString(sessionId))
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "{\"requestId\":\"x\",\"sessionSeq\":1,\"items\":[{}]}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(400);
  }

  @Test
  void missingSessionHeader_returns400() throws Exception {
    HttpResponse<String> response =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/api/v1/stage_orders"))
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "{\"requestId\":\"x\",\"sessionSeq\":1,\"items\":[{}]}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(400);
  }

  // ── helpers ─────────────────────────────────────────────────────────────────

  private JsonNode postJson(String path, Long sessionId, String body) throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(base + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (sessionId != null) {
      builder.header("X-EMS-Session", Long.toString(sessionId));
    }
    HttpResponse<String> response =
        client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    return mapper.readTree(response.body());
  }

  private JsonNode getJson(String pathAndQuery) throws Exception {
    HttpResponse<String> response =
        client.send(
            HttpRequest.newBuilder(URI.create(base + pathAndQuery)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    return mapper.readTree(response.body());
  }
}
