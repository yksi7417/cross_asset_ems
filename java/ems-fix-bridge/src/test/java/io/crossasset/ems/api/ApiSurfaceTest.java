/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.aaa.CredentialKind;
import io.crossasset.ems.aaa.InMemoryAaaEventLog;
import io.crossasset.ems.aaa.InMemoryAaaService;
import io.crossasset.ems.aaa.LogonCredentials;
import io.crossasset.ems.aaa.LogonOutcome;
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
import io.crossasset.ems.validator.ValidatorPipeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ApiSurface}: native handshake via AAA, batch request/response envelope with
 * idempotency and sequence checks, the order/route operation set, and pub/sub with cursor resume.
 * Per arch-api-first.md, task 8.4.
 */
class ApiSurfaceTest {

  private static final String TOKEN = "tok-api-1";
  private static final String FIGI = "BBG000BLNNH6";
  private static final String VENUE = "XNAS";

  private InMemoryAaaService aaa;
  private ApiSurface api;
  private InMemoryRouteManager router;
  private SubscriptionRegistry subscriptions;
  private List<ApiEvent> delivered;
  private long sessionId;
  private long seq;

  @BeforeEach
  void setUp() {
    aaa =
        new InMemoryAaaService(
            new InMemoryAaaEventLog(),
            null,
            new io.crossasset.ems.transport.session.SequenceRecoveryService(() -> 0L));
    InMemorySecurityMasterService secMaster = new InMemorySecurityMasterService();
    ValidatorPipeline pipeline = new LayeredValidatorPipeline(aaa, secMaster, null);
    InMemoryStagedOrderManager som = new InMemoryStagedOrderManager(pipeline);
    router = new InMemoryRouteManager(som);
    subscriptions = new SubscriptionRegistry();
    delivered = new ArrayList<>();
    api =
        new ApiSurface(
            aaa, som, router, subscriptions, (sid, subId, event) -> delivered.add(event));

    aaa.registerCredential(TOKEN, "firm-a", "desk-1", "trader-1", Set.of());
    // Native client handshake: AAA logon establishes the resumable session channel (8.9).
    LogonOutcome outcome = aaa.logon(LogonCredentials.fresh(CredentialKind.TOKEN, TOKEN));
    sessionId = ((LogonOutcome.Accepted) outcome).session().sessionId();
    seq = 1;

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
  }

  // ── Envelope ────────────────────────────────────────────────────────────────

  @Test
  void unknownSession_rejectsEveryItemWithSes1002() {
    ApiResponse response =
        api.execute(
            new ApiRequest(
                "req-1", 999_999L, 1, ApiOperation.STAGE_ORDERS, List.of(stageItem("CL-1"))));
    assertThat(response.summary().rejected()).isEqualTo(1);
    assertThat(response.results().get(0).errorCode()).isEqualTo("EMS-SES-1002");
  }

  @Test
  void duplicateRequestId_returnsCachedResponseWithoutReExecution() {
    long retrySeq = seq++;
    ApiResponse first =
        api.execute(
            new ApiRequest(
                "req-dup",
                sessionId,
                retrySeq,
                ApiOperation.STAGE_ORDERS,
                List.of(stageItem("CL-1"))));
    // A client retry re-sends the same envelope: same requestId, same sessionSeq.
    ApiResponse second =
        api.execute(
            new ApiRequest(
                "req-dup",
                sessionId,
                retrySeq,
                ApiOperation.STAGE_ORDERS,
                List.of(stageItem("CL-1"))));
    assertThat(second).isSameAs(first);
    // Only one OrderStaged event was ever published to the blotter.
    String sub = subscribeBlotter(1);
    long staged = delivered.stream().filter(e -> e.type().equals("OrderStaged")).count();
    assertThat(staged).isEqualTo(1);
    assertThat(sub).isNotNull();
  }

  @Test
  void sequenceGap_rejectsWholeRequest() {
    seq = 50; // session expects 1
    ApiResponse response =
        api.execute(
            new ApiRequest(
                "req-gap", sessionId, seq, ApiOperation.STAGE_ORDERS, List.of(stageItem("CL-1"))));
    assertThat(response.results().get(0).errorCode()).isEqualTo("EMS-SES-2001");
  }

  @Test
  void itemTypeMismatch_rejectsThatItem() {
    ApiResponse response =
        api.execute(
            new ApiRequest(
                "req-mm",
                sessionId,
                seq++,
                ApiOperation.STAGE_ORDERS,
                List.of(new ApiItem.CancelOrder("EMS-ORD-1"))));
    assertThat(response.results().get(0).status()).isEqualTo(ItemResult.Status.REJECTED);
    assertThat(response.results().get(0).errorCode()).isEqualTo("EMS-ORD-1001");
  }

  // ── Order operations ────────────────────────────────────────────────────────

  @Test
  void stageOrders_batchOfTwo_returnsPositionMatchedResults() {
    ApiResponse response =
        api.execute(
            new ApiRequest(
                "req-2",
                sessionId,
                seq++,
                ApiOperation.STAGE_ORDERS,
                List.of(stageItem("CL-A"), stageItem("CL-B"))));
    assertThat(response.summary().ok()).isEqualTo(2);
    assertThat(response.results()).hasSize(2);
    assertThat(response.results().get(0).refId()).isNotNull();
    assertThat(response.results().get(1).refId()).isNotNull();
  }

  @Test
  void stageOrders_partialFailure_reportsPerItem() {
    ApiItem bad = new ApiItem.StageOrder("CL-BAD", "BBG000UNKNOWN", 1, 100, null, "acc-1", 0);
    ApiResponse response =
        api.execute(
            new ApiRequest(
                "req-3",
                sessionId,
                seq++,
                ApiOperation.STAGE_ORDERS,
                List.of(stageItem("CL-OK"), bad)));
    assertThat(response.summary().ok()).isEqualTo(1);
    assertThat(response.summary().rejected()).isEqualTo(1);
    assertThat(response.results().get(0).status()).isEqualTo(ItemResult.Status.ACCEPTED);
    assertThat(response.results().get(1).status()).isEqualTo(ItemResult.Status.REJECTED);
  }

  @Test
  void fullOrderLifecycle_stageAmendReadyRouteCancelRoute() {
    String orderId = stage("req-l1", "CL-LC").results().get(0).refId();

    ApiResponse amend =
        api.execute(
            new ApiRequest(
                "req-l2",
                sessionId,
                seq++,
                ApiOperation.AMEND_ORDERS,
                List.of(new ApiItem.AmendOrder(orderId, 200L, null))));
    assertThat(amend.summary().ok()).isEqualTo(1);

    ApiResponse ready =
        api.execute(
            new ApiRequest(
                "req-l3",
                sessionId,
                seq++,
                ApiOperation.MARK_READY,
                List.of(new ApiItem.MarkReady(orderId))));
    assertThat(ready.summary().ok()).isEqualTo(1);

    ApiResponse route =
        api.execute(
            new ApiRequest(
                "req-l4",
                sessionId,
                seq++,
                ApiOperation.ROUTE_ORDERS,
                List.of(new ApiItem.RouteOrder(orderId, VENUE, 200, null))));
    assertThat(route.results().get(0).errorMessage()).isNull();
    assertThat(route.summary().ok()).isEqualTo(1);
    String routeId = route.results().get(0).refId();
    assertThat(routeId).startsWith("EMS-RTE-");

    // Venue acks the route (adapter-side event, not a client operation) so cancel is legal.
    router.acknowledgeRoute(routeId);

    ApiResponse cancelRoute =
        api.execute(
            new ApiRequest(
                "req-l5",
                sessionId,
                seq++,
                ApiOperation.CANCEL_ROUTES,
                List.of(new ApiItem.CancelRoute(routeId))));
    assertThat(cancelRoute.summary().ok()).isEqualTo(1);
  }

  @Test
  void cancelOrder_unroutedOrder_accepted() {
    String orderId = stage("req-c1", "CL-CXL").results().get(0).refId();
    ApiResponse cancel =
        api.execute(
            new ApiRequest(
                "req-c2",
                sessionId,
                seq++,
                ApiOperation.CANCEL_ORDERS,
                List.of(new ApiItem.CancelOrder(orderId))));
    assertThat(cancel.summary().ok()).isEqualTo(1);
  }

  // ── Batch semantics (8.5) ───────────────────────────────────────────────────

  @Test
  void onErrorStop_defersItemsAfterFirstRejection() {
    ApiItem bad = new ApiItem.StageOrder("CL-BAD", "BBG000UNKNOWN", 1, 100, null, "acc-1", 0);
    ApiResponse response =
        api.execute(
            new ApiRequest(
                "req-stop",
                sessionId,
                seq++,
                ApiOperation.STAGE_ORDERS,
                List.of(stageItem("CL-S1"), bad, stageItem("CL-S2")),
                new BatchOptions(true, BatchOptions.OnError.STOP)));
    assertThat(response.results().get(0).status()).isEqualTo(ItemResult.Status.ACCEPTED);
    assertThat(response.results().get(1).status()).isEqualTo(ItemResult.Status.REJECTED);
    assertThat(response.results().get(2).status()).isEqualTo(ItemResult.Status.DEFERRED);
    assertThat(response.summary().deferred()).isEqualTo(1);
  }

  @Test
  void allOrNothing_anyRejection_voidsAppliedItems() {
    ApiItem bad = new ApiItem.StageOrder("CL-BAD", "BBG000UNKNOWN", 1, 100, null, "acc-1", 0);
    ApiResponse response =
        api.execute(
            new ApiRequest(
                "req-aon",
                sessionId,
                seq++,
                ApiOperation.STAGE_ORDERS,
                List.of(stageItem("CL-A1"), bad),
                new BatchOptions(false, BatchOptions.OnError.CONTINUE)));
    assertThat(response.summary().ok()).isZero();
    assertThat(response.results().get(0).status()).isEqualTo(ItemResult.Status.REJECTED);
    assertThat(response.results().get(0).errorMessage()).contains("Voided");
  }

  @Test
  void allOrNothing_allAccepted_appliesNormally() {
    ApiResponse response =
        api.execute(
            new ApiRequest(
                "req-aon-ok",
                sessionId,
                seq++,
                ApiOperation.STAGE_ORDERS,
                List.of(stageItem("CL-A2"), stageItem("CL-A3")),
                new BatchOptions(false, BatchOptions.OnError.CONTINUE)));
    assertThat(response.summary().ok()).isEqualTo(2);
  }

  @Test
  void allOrNothing_unsupportedOperation_rejectsWholeRequest() {
    ApiResponse response =
        api.execute(
            new ApiRequest(
                "req-aon-bad",
                sessionId,
                seq++,
                ApiOperation.CANCEL_ORDERS,
                List.of(new ApiItem.CancelOrder("EMS-ORD-1")),
                new BatchOptions(false, BatchOptions.OnError.CONTINUE)));
    assertThat(response.results().get(0).status()).isEqualTo(ItemResult.Status.REJECTED);
    assertThat(response.results().get(0).errorMessage()).contains("all-or-nothing");
  }

  // ── Pub/sub ─────────────────────────────────────────────────────────────────

  @Test
  void subscribe_blotterFromSeq1_replaysPriorEventsThenLive() {
    stage("req-s1", "CL-E1");
    stage("req-s2", "CL-E2");
    subscribeBlotter(1);
    assertThat(delivered).hasSize(2); // replayed both staged events
    stage("req-s3", "CL-E3");
    assertThat(delivered).hasSize(3); // live event followed
    assertThat(delivered.get(2).type()).isEqualTo("OrderStaged");
    assertThat(delivered.get(0).seq()).isLessThan(delivered.get(2).seq());
  }

  @Test
  void subscribe_midCursor_skipsOlderEvents() {
    stage("req-m1", "CL-M1");
    stage("req-m2", "CL-M2");
    stage("req-m3", "CL-M3");
    subscribeBlotter(3);
    assertThat(delivered).hasSize(1);
    assertThat(delivered.get(0).seq()).isEqualTo(3);
  }

  @Test
  void unsubscribe_stopsDelivery() {
    String subId = subscribeBlotter(1);
    ApiResponse unsub =
        api.execute(
            new ApiRequest(
                "req-u1",
                sessionId,
                seq++,
                ApiOperation.UNSUBSCRIBE,
                List.of(new ApiItem.Unsubscribe(subId))));
    assertThat(unsub.summary().ok()).isEqualTo(1);
    int before = delivered.size();
    stage("req-u2", "CL-AFTER");
    assertThat(delivered).hasSize(before);
  }

  @Test
  void perOrderTopic_receivesOnlyThatOrdersEvents() {
    String orderId = stage("req-t1", "CL-T1").results().get(0).refId();
    stage("req-t2", "CL-T2");
    api.execute(
        new ApiRequest(
            "req-t3",
            sessionId,
            seq++,
            ApiOperation.SUBSCRIBE,
            List.of(new ApiItem.Subscribe("order." + orderId, 1))));
    assertThat(delivered).hasSize(1);
    assertThat(delivered.get(0).refId()).isEqualTo(orderId);
  }

  // ── helpers ─────────────────────────────────────────────────────────────────

  private ApiItem.StageOrder stageItem(String clOrdId) {
    return new ApiItem.StageOrder(clOrdId, FIGI, 1, 100, null, "acc-1", 0);
  }

  private ApiResponse stage(String requestId, String clOrdId) {
    return api.execute(
        new ApiRequest(
            requestId, sessionId, seq++, ApiOperation.STAGE_ORDERS, List.of(stageItem(clOrdId))));
  }

  private String subscribeBlotter(long fromSeq) {
    ApiResponse response =
        api.execute(
            new ApiRequest(
                "req-sub-" + fromSeq + "-" + seq,
                sessionId,
                seq++,
                ApiOperation.SUBSCRIBE,
                List.of(new ApiItem.Subscribe(ApiSurface.TOPIC_ORDERS, fromSeq))));
    return response.results().get(0).refId();
  }
}
