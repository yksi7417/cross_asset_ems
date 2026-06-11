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
import io.crossasset.ems.validator.LayeredValidatorPipeline;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Ticket-support tests (task 18.2): the instrument lookup that drives per-asset-class ticket
 * layouts, and PREVIEW_VALIDATE — the server-authoritative dry-run behind the ticket's live
 * per-field feedback (same LayeredValidatorPipeline as the stage path; no state change, no events).
 */
class TicketSupportTest {

  private static final String FIGI = "BBG000BLNNH6";

  private final ObjectMapper mapper = new ObjectMapper();
  private final HttpClient client = HttpClient.newHttpClient();
  private final SubscriptionRegistry subscriptions = new SubscriptionRegistry();
  private RestHttpServer server;
  private String base;
  private long sessionId;
  private int seq = 1;

  @BeforeEach
  void setUp() throws Exception {
    InMemoryAaaService aaa = new InMemoryAaaService(new InMemoryAaaEventLog());
    aaa.registerCredential("tok-ticket", "firm-a", "desk-1", "trader-t", Set.of());
    InMemorySecurityMasterService secMaster = new InMemorySecurityMasterService();
    secMaster.publish(
        SecurityMasterSnapshot.EMPTY.apply(
            new SecurityMasterEvent.InstrumentCreated(
                new InstrumentVersioned(
                    new InstrumentCore(
                        FIGI,
                        "IID-1",
                        null,
                        null,
                        AssetClass.EQUITY,
                        InstrumentType.COMMON_STOCK,
                        "IBM Corp",
                        "International Business Machines",
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
                        1_000_000L),
                    null),
                1L)));
    LayeredValidatorPipeline pipeline = new LayeredValidatorPipeline(aaa, secMaster, null);
    InMemoryStagedOrderManager som = new InMemoryStagedOrderManager(pipeline);
    ApiSurface api =
        new ApiSurface(
            aaa,
            som,
            new InMemoryRouteManager(som),
            subscriptions,
            (sid, subId, event) -> {},
            pipeline);
    server = new RestHttpServer(new RestEdgeBinding(aaa, api, subscriptions, secMaster), 0);
    server.start();
    base = "http://localhost:" + server.port();

    JsonNode logon = post("/api/v1/logon", null, "{\"token\":\"tok-ticket\"}");
    sessionId = logon.path("sessionId").asLong();
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  private JsonNode post(String path, Long session, String body) throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(base + path))
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (session != null) {
      builder.header("X-EMS-Session", String.valueOf(session));
    }
    HttpResponse<String> response =
        client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    return mapper.readTree(response.body());
  }

  private JsonNode preview(String figi) throws Exception {
    return post(
        "/api/v1/preview_validate",
        sessionId,
        "{\"requestId\":\"prev-"
            + seq
            + "\",\"sessionSeq\":"
            + (seq++)
            + ",\"items\":[{\"figi\":\""
            + figi
            + "\"}]}");
  }

  @Test
  void instrumentLookup_servesTicketLayoutFields() throws Exception {
    HttpResponse<String> response =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/api/v1/instruments/" + FIGI)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode body = mapper.readTree(response.body());
    assertThat(body.path("figi").asText()).isEqualTo(FIGI);
    assertThat(body.path("name").asText()).isEqualTo("IBM Corp");
    assertThat(body.path("assetClass").asText()).isEqualTo("EQUITY");
    assertThat(body.path("currency").asText()).isEqualTo("USD");
    assertThat(body.path("settlement").asText()).isEqualTo("T_PLUS_2");
  }

  @Test
  void instrumentLookup_unknownFigi_404() throws Exception {
    HttpResponse<String> response =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/api/v1/instruments/BBG0UNKNOWN0"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(404);
  }

  @Test
  void preview_knownInstrument_accepted() throws Exception {
    JsonNode response = preview(FIGI);
    assertThat(response.path("summary").path("ok").asInt()).isEqualTo(1);
    assertThat(response.path("results").get(0).path("refId").asText()).isEqualTo(FIGI);
  }

  @Test
  void preview_unknownInstrument_rejectsWithCatalogCodeAndAdminHint() throws Exception {
    JsonNode response = preview("BBG0UNKNOWN0");
    JsonNode result = response.path("results").get(0);
    assertThat(result.path("status").asText()).isEqualTo("REJECTED");
    assertThat(result.path("errorCode").asText()).isEqualTo("EMS-REF-2001");
    assertThat(result.path("errorMessage").asText())
        .contains("not present in security master")
        .contains("Contact:");
  }

  @Test
  void preview_changesNoState_publishesNoEvents() throws Exception {
    preview(FIGI);
    preview("BBG0UNKNOWN0");
    assertThat(subscriptions.fetch(ApiSurface.TOPIC_ORDERS, 1, 100)).isEmpty();

    // The sequenced channel stays intact: a real stage with the next seq succeeds.
    JsonNode stage =
        post(
            "/api/v1/stage_orders",
            sessionId,
            "{\"requestId\":\"st-1\",\"sessionSeq\":"
                + (seq++)
                + ",\"items\":[{\"clOrdId\":\"CL-1\",\"figi\":\""
                + FIGI
                + "\",\"side\":1,\"qty\":100,\"account\":\"acc-1\"}]}");
    assertThat(stage.path("summary").path("ok").asInt()).isEqualTo(1);
  }

  @Test
  void preview_withoutPipeline_rejectsCleanly() throws Exception {
    // A surface constructed without the preview pipeline (legacy ctor) still answers.
    InMemoryAaaService aaa2 = new InMemoryAaaService(new InMemoryAaaEventLog());
    aaa2.registerCredential("tok2", "f", "d", "u", Set.of());
    InMemoryStagedOrderManager som2 =
        new InMemoryStagedOrderManager(new LayeredValidatorPipeline(aaa2, null, null));
    ApiSurface bare =
        new ApiSurface(
            aaa2,
            som2,
            new InMemoryRouteManager(som2),
            new SubscriptionRegistry(),
            (a, b, c) -> {});
    var outcome =
        aaa2.logon(
            io.crossasset.ems.aaa.LogonCredentials.fresh(
                io.crossasset.ems.aaa.CredentialKind.TOKEN, "tok2"));
    long session2 = ((io.crossasset.ems.aaa.LogonOutcome.Accepted) outcome).session().sessionId();
    var response =
        bare.execute(
            new io.crossasset.ems.api.ApiRequest(
                "p1",
                session2,
                1,
                io.crossasset.ems.api.ApiOperation.PREVIEW_VALIDATE,
                java.util.List.of(new io.crossasset.ems.api.ApiItem.PreviewOrder(FIGI)),
                io.crossasset.ems.api.BatchOptions.DEFAULT));
    assertThat(response.results().get(0).errorCode()).isEqualTo("EMS-CFG-7001");
  }
}
