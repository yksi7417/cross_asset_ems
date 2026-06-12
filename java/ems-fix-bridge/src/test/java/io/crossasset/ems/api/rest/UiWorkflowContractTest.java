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
import io.crossasset.ems.api.basket.BasketService;
import io.crossasset.ems.api.blotter.BlotterPublisher;
import io.crossasset.ems.api.blotter.BlotterRouteManager;
import io.crossasset.ems.api.blotter.BlotterStagedOrderManager;
import io.crossasset.ems.api.control.KillSwitchOrderGuard;
import io.crossasset.ems.api.control.KillSwitchRouteGuard;
import io.crossasset.ems.api.control.KillSwitchService;
import io.crossasset.ems.api.control.KillSwitchState;
import io.crossasset.ems.api.md.DeskWatchlist;
import io.crossasset.ems.api.md.MarketDataTopicBridge;
import io.crossasset.ems.api.notify.DesktopSink;
import io.crossasset.ems.api.notify.NotificationService;
import io.crossasset.ems.bulk.BulkOrderImporter;
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
import io.crossasset.ems.md.MdField;
import io.crossasset.ems.md.SimulatedFeed;
import io.crossasset.ems.oms.InMemoryRouteManager;
import io.crossasset.ems.oms.InMemoryStagedOrderManager;
import io.crossasset.ems.validator.LayeredValidatorPipeline;
import io.crossasset.ems.venue.esp.EspClickService;
import io.crossasset.ems.venue.esp.MockEspVenue;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The UI's workflow contract (task 18.19), pinned at the REST edge over the full demo-edge wiring:
 * every failure path the desktop must RENDER — exact catalog codes and shapes. QA round 1 (18.18,
 * docs/QA_REVIEW.md) found the UI discarding precisely this information; these tests keep the
 * contract honest from the server side while the Playwright specs keep the rendering honest from
 * the user's side. API-level on purpose: many, fast, no browser (test-trophy middle).
 */
class UiWorkflowContractTest {

  private static final String FIGI = "BBG000BLNNH6";
  private static final String EURUSD = "BBG0013HJJ31";

  private final ObjectMapper mapper = new ObjectMapper();
  private final HttpClient client = HttpClient.newHttpClient();
  private RestHttpServer server;
  private String base;
  private long session;
  private long untaggedSession;
  private int seq = 1;
  private int untaggedSeq = 1;
  private MockEspVenue espVenue;
  private NotificationService notifications;

  @BeforeEach
  void setUp() throws Exception {
    InMemoryAaaService aaa = new InMemoryAaaService(new InMemoryAaaEventLog());
    aaa.registerCredential("trader", "firm-a", "desk-1", "trader-1", Set.of("#kill-switch"));
    aaa.registerCredential("pleb", "firm-a", "desk-2", "pleb-1", Set.of());

    InMemorySecurityMasterService secMaster = new InMemorySecurityMasterService();
    secMaster.publish(
        SecurityMasterSnapshot.EMPTY.apply(
            new SecurityMasterEvent.InstrumentCreated(
                new InstrumentVersioned(equity(FIGI, "IBM Corp"), null), 1L)));

    SubscriptionRegistry subscriptions = new SubscriptionRegistry();
    LayeredValidatorPipeline pipeline = new LayeredValidatorPipeline(aaa, secMaster, null);
    BlotterPublisher blotter = new BlotterPublisher(subscriptions, () -> 1_000_000L);
    BlotterStagedOrderManager blotterSom =
        new BlotterStagedOrderManager(new InMemoryStagedOrderManager(pipeline), blotter);
    BlotterRouteManager blotterRoutes =
        new BlotterRouteManager(new InMemoryRouteManager(blotterSom), blotter);
    KillSwitchState killState = new KillSwitchState();
    KillSwitchOrderGuard som = new KillSwitchOrderGuard(blotterSom, killState, aaa);
    KillSwitchRouteGuard routes = new KillSwitchRouteGuard(blotterRoutes, killState, aaa, som);
    KillSwitchService kill =
        new KillSwitchService(aaa, som, routes, killState, subscriptions, () -> 7_000L);
    ApiSurface api =
        new ApiSurface(aaa, som, routes, subscriptions, (sid, subId, event) -> {}, pipeline);
    BasketService baskets =
        new BasketService(som, routes, new BulkOrderImporter(api), subscriptions);
    notifications = new NotificationService();
    notifications.registerSink(new DesktopSink(subscriptions));
    notifications.registerRule(
        new NotificationService.Rule(
            "any-critical",
            null,
            null,
            NotificationService.Severity.CRITICAL,
            null,
            "desk-1",
            Set.of(NotificationService.Channel.DESKTOP),
            true,
            60_000L,
            0,
            java.util.List.of()));
    SimulatedFeed feed = new SimulatedFeed("sim");
    DeskWatchlist watchlist =
        new DeskWatchlist(
            subscriptions, new MarketDataTopicBridge(subscriptions), feed, Set.of(MdField.LAST));
    espVenue = new MockEspVenue("LMAX", 2, 10L);
    EspClickService esp = new EspClickService();
    esp.attach(EURUSD, espVenue);

    server =
        new RestHttpServer(
            new RestEdgeBinding(
                aaa,
                api,
                subscriptions,
                secMaster,
                baskets,
                kill,
                notifications,
                null,
                null,
                esp,
                watchlist),
            0);
    server.start();
    base = "http://localhost:" + server.port();
    session = logon("trader");
    untaggedSession = logon("pleb");
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  // ── The error codes the UI renders, pinned ───────────────────────────────────

  @Test
  void cancelTerminalOrder_isEmsOrd3001_withHumanMessage() throws Exception {
    String orderId = stage("CT-1");
    op("cancel_orders", "[{\"orderId\":\"" + orderId + "\"}]");
    JsonNode second = first(op("cancel_orders", "[{\"orderId\":\"" + orderId + "\"}]"));

    assertThat(second.path("status").asText()).isEqualTo("REJECTED");
    assertThat(second.path("errorCode").asText()).isEqualTo("EMS-ORD-3001");
    assertThat(second.path("errorMessage").asText()).contains("terminal state CANCELED");
  }

  @Test
  void amendTerminalOrder_isEmsOrd3001() throws Exception {
    String orderId = stage("AT-1");
    op("cancel_orders", "[{\"orderId\":\"" + orderId + "\"}]");
    JsonNode amend = first(op("amend_orders", "[{\"orderId\":\"" + orderId + "\",\"qty\":500}]"));

    assertThat(amend.path("errorCode").asText()).isEqualTo("EMS-ORD-3001");
  }

  @Test
  void routeBeforeReady_isEmsRte4002() throws Exception {
    String orderId = stage("RR-1");
    JsonNode route =
        first(
            op(
                "route_orders",
                "[{\"orderId\":\"" + orderId + "\",\"venueMic\":\"XNAS\",\"qty\":100}]"));

    assertThat(route.path("errorCode").asText()).isEqualTo("EMS-RTE-4002");
    assertThat(route.path("errorMessage").asText()).contains("not ready");
  }

  @Test
  void routeBeyondRemaining_isEmsRte4003() throws Exception {
    String orderId = stage("RB-1");
    op("mark_ready", "[{\"orderId\":\"" + orderId + "\"}]");
    op("route_orders", "[{\"orderId\":\"" + orderId + "\",\"venueMic\":\"XNAS\",\"qty\":300}]");
    JsonNode over =
        first(
            op(
                "route_orders",
                "[{\"orderId\":\"" + orderId + "\",\"venueMic\":\"XNAS\",\"qty\":300}]"));

    assertThat(over.path("errorCode").asText()).isEqualTo("EMS-RTE-4003");
  }

  @Test
  void cancelUnknownOrder_hasCatalogCode() throws Exception {
    JsonNode unknown = first(op("cancel_orders", "[{\"orderId\":\"EMS-ORD-NOPE\"}]"));
    assertThat(unknown.path("status").asText()).isEqualTo("REJECTED");
    assertThat(unknown.path("errorCode").asText()).startsWith("EMS-");
    assertThat(unknown.path("errorMessage").asText()).isNotBlank();
  }

  @Test
  void wrongSession_rejectsWholeBatch_emsSes1002() throws Exception {
    HttpResponse<String> response =
        post(
            "/api/v1/cancel_orders",
            999_999L,
            "{\"requestId\":\"ws-1\",\"sessionSeq\":1,\"items\":[{\"orderId\":\"X\"}]}");
    JsonNode result = mapper.readTree(response.body()).path("results").get(0);
    assertThat(result.path("errorCode").asText()).isEqualTo("EMS-SES-1002");
  }

  // ── Workflow round-trips the UI drives ───────────────────────────────────────

  @Test
  void ticketLifecycle_stageReadyRouteAmendCancel() throws Exception {
    String orderId = stage("TL-1");
    assertThat(
            first(op("amend_orders", "[{\"orderId\":\"" + orderId + "\",\"qty\":400}]"))
                .path("status")
                .asText())
        .isEqualTo("ACCEPTED");
    assertThat(
            first(op("mark_ready", "[{\"orderId\":\"" + orderId + "\"}]")).path("status").asText())
        .isEqualTo("ACCEPTED");
    JsonNode routed =
        first(
            op(
                "route_orders",
                "[{\"orderId\":\"" + orderId + "\",\"venueMic\":\"XNAS\",\"qty\":200}]"));
    assertThat(routed.path("status").asText()).isEqualTo("ACCEPTED");
    // Contract fact the UI must respect (QA #16): a SENT route (no venue ack yet) cannot be
    // canceled — the Route FSM dispatches cancels only from WORKING/PARTIALLY_FILLED.
    JsonNode cancelSent =
        first(op("cancel_routes", "[{\"routeId\":\"" + routed.path("refId").asText() + "\"}]"));
    assertThat(cancelSent.path("status").asText()).isEqualTo("REJECTED");
    assertThat(cancelSent.path("errorCode").asText()).isEqualTo("EMS-RTE-5002");
    assertThat(
            first(op("cancel_orders", "[{\"orderId\":\"" + orderId + "\"}]"))
                .path("status")
                .asText())
        .isEqualTo("ACCEPTED");
  }

  @Test
  void batchSemantics_mixedResults_positionMatched() throws Exception {
    String live = stage("BM-1");
    String dead = stage("BM-2");
    op("cancel_orders", "[{\"orderId\":\"" + dead + "\"}]");

    JsonNode results =
        op("cancel_orders", "[{\"orderId\":\"" + live + "\"},{\"orderId\":\"" + dead + "\"}]");
    assertThat(results.get(0).path("status").asText()).isEqualTo("ACCEPTED");
    assertThat(results.get(1).path("status").asText()).isEqualTo("REJECTED");
    assertThat(results.get(1).path("errorCode").asText()).isEqualTo("EMS-ORD-3001");
  }

  @Test
  void watchlist_addDuplicateIs409_removeMissingIs404() throws Exception {
    HttpResponse<String> add =
        post("/api/v1/watchlist/desk-1", session, "{\"figi\":\"" + FIGI + "\"}");
    assertThat(add.statusCode()).isEqualTo(200);
    HttpResponse<String> duplicate =
        post("/api/v1/watchlist/desk-1", session, "{\"figi\":\"" + FIGI + "\"}");
    assertThat(duplicate.statusCode()).isEqualTo(409);

    HttpResponse<String> remove = delete("/api/v1/watchlist/desk-1/" + FIGI, session);
    assertThat(remove.statusCode()).isEqualTo(200);
    HttpResponse<String> missing = delete("/api/v1/watchlist/desk-1/" + FIGI, session);
    assertThat(missing.statusCode()).isEqualTo(404);
  }

  @Test
  void killSwitch_requiresTag_thenLockoutCodeOnStage() throws Exception {
    // The untagged session is refused with the catalog code at 403.
    HttpResponse<String> refused =
        post(
            "/api/v1/kill",
            untaggedSession,
            "{\"kind\":\"FIRM\",\"value\":\"firm-a\",\"reason\":\"nope\"}");
    assertThat(refused.statusCode()).isEqualTo(403);
    assertThat(refused.body()).contains("EMS-AUT-9601");

    // Tagged engage → staging rejects with the lockout code the ticket renders.
    assertThat(
            post(
                    "/api/v1/kill",
                    session,
                    "{\"kind\":\"FIRM\",\"value\":\"firm-a\",\"reason\":\"drill\"}")
                .statusCode())
        .isEqualTo(200);
    JsonNode locked =
        first(
            op(
                "stage_orders",
                "[{\"clOrdId\":\"KL-1\",\"figi\":\""
                    + FIGI
                    + "\",\"side\":1,\"qty\":100,\"account\":\"a\"}]"));
    assertThat(locked.path("errorCode").asText()).isEqualTo("EMS-ORD-9601");
    assertThat(
            post(
                    "/api/v1/kill/release",
                    session,
                    "{\"kind\":\"FIRM\",\"value\":\"firm-a\",\"reason\":\"done\"}")
                .statusCode())
        .isEqualTo(200);
  }

  @Test
  void espClick_slippageGuard_neverReachesTheDealer() throws Exception {
    espVenue.post(
        EURUSD, 1_0848L, 5_000_000, 1_0852L, 5_000_000, System.currentTimeMillis(), 60_000L);
    HttpResponse<String> response =
        post(
            "/api/v1/esp/click",
            session,
            "{\"figi\":\""
                + EURUSD
                + "\",\"side\":1,\"qty\":1000000,\"expectedPx\":10000,\"maxSlippageBp\":5}");
    JsonNode body = mapper.readTree(response.body());
    assertThat(body.path("status").asText()).isEqualTo("REJECTED");
    assertThat(body.path("reason").asText()).isEqualTo("SLIPPAGE_GUARD");
    assertThat(body.path("detail").asText()).contains("not sent to venue");
  }

  @Test
  void notifications_doubleAckIs409() throws Exception {
    // Publish directly (the demo edge's stream bridge is its own wiring, not this contract).
    notifications.publish(
        "qa",
        NotificationService.Kind.ALERT,
        NotificationService.Severity.CRITICAL,
        "qa-alert",
        "body",
        java.util.List.of(),
        1_000L);
    HttpResponse<String> events = get("/api/v1/events?topic=notify.desk-1&from=1&max=50");
    JsonNode list = mapper.readTree(events.body()).path("events");
    assertThat(list.size()).isGreaterThan(0);
    String id =
        mapper.readTree(list.get(0).path("payload").asText()).path("notificationId").asText();

    assertThat(post("/api/v1/notifications/" + id + "/ack", session, "").statusCode())
        .isEqualTo(200);
    assertThat(post("/api/v1/notifications/" + id + "/ack", session, "").statusCode())
        .isEqualTo(409);
  }

  @Test
  void auditTrail_orderHistoryIsTheFullTimelineInEventOrder() throws Exception {
    String orderId = stage("HIST-1");
    op("mark_ready", "[{\"orderId\":\"" + orderId + "\"}]");
    JsonNode routed =
        first(
            op(
                "route_orders",
                "[{\"orderId\":\"" + orderId + "\",\"venueMic\":\"XNAS\",\"qty\":300}]"));
    String routeId = routed.path("refId").asText();

    HttpResponse<String> response =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/api/v1/orders/" + orderId + "/history"))
                .header("X-EMS-Session", String.valueOf(session))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode events = mapper.readTree(response.body()).path("events");
    // staged + ready + routing images for the order, plus the route's SENT image.
    assertThat(events.size()).isGreaterThanOrEqualTo(4);
    assertThat(events.get(0).path("kind").asText()).isEqualTo("ORDER");
    assertThat(events.get(0).path("row").path("subState").asText()).isEqualTo("NEW");
    java.util.List<String> kinds = new java.util.ArrayList<>();
    java.util.List<String> orderSubStates = new java.util.ArrayList<>();
    for (JsonNode event : events) {
      kinds.add(event.path("kind").asText());
      if ("ORDER".equals(event.path("kind").asText())) {
        orderSubStates.add(event.path("row").path("subState").asText());
      }
    }
    assertThat(kinds).contains("ROUTE");
    assertThat(orderSubStates).containsExactly("NEW", "READY", "ROUTING");

    // The route's own timeline sees only that route (+ its fills).
    HttpResponse<String> routeHistory =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/api/v1/routes/" + routeId + "/history"))
                .header("X-EMS-Session", String.valueOf(session))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    JsonNode routeEvents = mapper.readTree(routeHistory.body()).path("events");
    assertThat(routeEvents.size()).isGreaterThanOrEqualTo(1);
    for (JsonNode event : routeEvents) {
      assertThat(event.path("row").path("routeId").asText()).isEqualTo(routeId);
    }

    // Session-gated like every other read.
    assertThat(get("/api/v1/orders/" + orderId + "/history").statusCode()).isEqualTo(400);
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private String stage(String clOrdId) throws Exception {
    JsonNode result =
        first(
            op(
                "stage_orders",
                "[{\"clOrdId\":\""
                    + clOrdId
                    + "\",\"figi\":\""
                    + FIGI
                    + "\",\"side\":1,\"qty\":300,\"account\":\"qa\"}]"));
    assertThat(result.path("status").asText()).isEqualTo("ACCEPTED");
    return result.path("refId").asText();
  }

  private JsonNode op(String operation, String itemsJson) throws Exception {
    HttpResponse<String> response =
        post(
            "/api/v1/" + operation,
            session,
            "{\"requestId\":\"qa-"
                + seq
                + "\",\"sessionSeq\":"
                + (seq++)
                + ",\"items\":"
                + itemsJson
                + "}");
    return mapper.readTree(response.body()).path("results");
  }

  private static JsonNode first(JsonNode results) {
    return results.get(0);
  }

  private long logon(String token) throws Exception {
    HttpResponse<String> response = post("/api/v1/logon", null, "{\"token\":\"" + token + "\"}");
    return mapper.readTree(response.body()).path("sessionId").asLong();
  }

  private HttpResponse<String> post(String path, Long sessionId, String body) throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(base + path))
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (sessionId != null) {
      builder.header("X-EMS-Session", String.valueOf(sessionId));
    }
    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> delete(String path, long sessionId) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create(base + path))
            .DELETE()
            .header("X-EMS-Session", String.valueOf(sessionId))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> get(String path) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static InstrumentCore equity(String figi, String name) {
    return new InstrumentCore(
        figi,
        "IID-" + figi,
        null,
        null,
        AssetClass.EQUITY,
        InstrumentType.COMMON_STOCK,
        name,
        name,
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
  }
}
