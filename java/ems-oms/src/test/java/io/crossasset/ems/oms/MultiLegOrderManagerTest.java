/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.*;

import io.crossasset.ems.aaa.CredentialKind;
import io.crossasset.ems.aaa.InMemoryAaaEventLog;
import io.crossasset.ems.aaa.InMemoryAaaService;
import io.crossasset.ems.aaa.LogonCredentials;
import io.crossasset.ems.aaa.LogonOutcome;
import io.crossasset.ems.fsm.generated.MultiLegFsmState;
import io.crossasset.ems.fsm.generated.RouteFsmState;
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
import io.crossasset.ems.validator.LayeredValidatorPipeline;
import io.crossasset.ems.validator.ValidatorPipeline;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link InMemoryMultiLegOrderManager}. Covers package staging validation
 * (EMS-ORD-4001/4002/4003), per-mode dispatch, SEQUENCED leg gating, ALL_OR_NONE cascade cancel,
 * LEGS_INDEPENDENT partial outcomes, and package-level cancel. Per arch-multileg.md, task 7.4.
 */
class MultiLegOrderManagerTest {

  private static final String TOKEN = "tok-trader-1";
  private static final String FIGI_SPOT = "BBG000BLNNH6";
  private static final String FIGI_FWD = "BBG000B9XRY4";
  private static final String VENUE = "FXCO";
  private static final String VENUE_B = "XNAS";
  private static final int BUY = 1;
  private static final int SELL = 2;
  private static final int TIF_DAY = 0;

  private StagedOrderManager som;
  private InMemoryRouteManager router;
  private InMemoryMultiLegOrderManager mlm;
  private long sessionId;

  @BeforeEach
  void setUp() {
    InMemoryAaaService aaaService = new InMemoryAaaService(new InMemoryAaaEventLog());
    InMemorySecurityMasterService secMaster = new InMemorySecurityMasterService();
    ValidatorPipeline pipeline = new LayeredValidatorPipeline(aaaService, secMaster, null);
    som = new InMemoryStagedOrderManager(pipeline);
    router = new InMemoryRouteManager(som);
    mlm = new InMemoryMultiLegOrderManager(som, router, pipeline);

    aaaService.registerCredential(TOKEN, "firm-a", "desk-1", "trader-1", Set.of());
    LogonOutcome outcome = aaaService.logon(LogonCredentials.fresh(CredentialKind.TOKEN, TOKEN));
    sessionId = ((LogonOutcome.Accepted) outcome).session().sessionId();

    SecurityMasterSnapshot snap =
        SecurityMasterSnapshot.EMPTY
            .apply(
                new SecurityMasterEvent.InstrumentCreated(
                    new InstrumentVersioned(activeInstrument(FIGI_SPOT), null), 1L))
            .apply(
                new SecurityMasterEvent.InstrumentCreated(
                    new InstrumentVersioned(activeInstrument(FIGI_FWD), null), 2L));
    secMaster.publish(snap);
  }

  // --- stage() validation ---

  @Test
  void stage_validSwapAllOrNone_readyWithTwoLegs() {
    MultiLegStageResult result = mlm.stage(swapRequest("req-1", "PKG-1"));
    MultiLegOrder order = ((MultiLegStageResult.Staged) result).order();
    assertEquals(MultiLegFsmState.READY, order.fsmState());
    assertEquals(2, order.legs().size());
    assertEquals(2, order.fsmContext().totalLegs());
    assertEquals("PKG-1", order.fsmContext().packageId());
    assertTrue(order.legs().stream().allMatch(l -> l.state() == LegState.PENDING));
  }

  @Test
  void stage_allOrNoneWithoutPackageId_rejected4002AndPersistedRejected() {
    MultiLegStageResult result = mlm.stage(swapRequest("req-1", null));
    MultiLegStageResult.Rejected rej = assertInstanceOf(MultiLegStageResult.Rejected.class, result);
    assertEquals("EMS-ORD-4002", rej.rejectCode());
  }

  @Test
  void stage_allOrNoneHeterogeneousVenues_rejected4001() {
    MultiLegOrderRequest request =
        new MultiLegOrderRequest(
            "req-1",
            sessionId,
            "CL-req-1",
            MultiLegKind.SWAP,
            ExecutionMode.ALL_OR_NONE,
            List.of(
                new LegRequest(1, FIGI_SPOT, BUY, 1_000_000, null, VENUE),
                new LegRequest(-1, FIGI_FWD, SELL, 1_000_000, null, VENUE_B)),
            "PKG-1",
            null,
            "acc-1",
            TIF_DAY);
    MultiLegStageResult.Rejected rej =
        assertInstanceOf(MultiLegStageResult.Rejected.class, mlm.stage(request));
    assertEquals("EMS-ORD-4001", rej.rejectCode());
  }

  @Test
  void stage_fewerThanTwoLegs_rejected4001() {
    MultiLegOrderRequest request =
        new MultiLegOrderRequest(
            "req-1",
            sessionId,
            "CL-req-1",
            MultiLegKind.CUSTOM,
            ExecutionMode.LEGS_INDEPENDENT,
            List.of(new LegRequest(1, FIGI_SPOT, BUY, 100, null, VENUE)),
            null,
            null,
            "acc-1",
            TIF_DAY);
    MultiLegStageResult.Rejected rej =
        assertInstanceOf(MultiLegStageResult.Rejected.class, mlm.stage(request));
    assertEquals("EMS-ORD-4001", rej.rejectCode());
  }

  @Test
  void stage_legQtyZero_rejected2001() {
    MultiLegOrderRequest request =
        new MultiLegOrderRequest(
            "req-1",
            sessionId,
            "CL-req-1",
            MultiLegKind.SPREAD,
            ExecutionMode.LEGS_INDEPENDENT,
            List.of(
                new LegRequest(1, FIGI_SPOT, BUY, 0, null, VENUE),
                new LegRequest(-1, FIGI_FWD, SELL, 100, null, VENUE)),
            null,
            null,
            "acc-1",
            TIF_DAY);
    MultiLegStageResult.Rejected rej =
        assertInstanceOf(MultiLegStageResult.Rejected.class, mlm.stage(request));
    assertEquals("EMS-ORD-2001", rej.rejectCode());
  }

  @Test
  void stage_sequencePolicyOnNonSequencedMode_rejected4003() {
    MultiLegOrderRequest request =
        new MultiLegOrderRequest(
            "req-1",
            sessionId,
            "CL-req-1",
            MultiLegKind.SWAP,
            ExecutionMode.ALL_OR_NONE,
            twoLegs(),
            "PKG-1",
            "spot_first",
            "acc-1",
            TIF_DAY);
    MultiLegStageResult.Rejected rej =
        assertInstanceOf(MultiLegStageResult.Rejected.class, mlm.stage(request));
    assertEquals("EMS-ORD-4003", rej.rejectCode());
  }

  @Test
  void stage_spotFirstPolicyOnSpreadKind_rejected4003() {
    MultiLegOrderRequest request =
        new MultiLegOrderRequest(
            "req-1",
            sessionId,
            "CL-req-1",
            MultiLegKind.SPREAD,
            ExecutionMode.SEQUENCED,
            twoLegs(),
            null,
            "spot_first",
            "acc-1",
            TIF_DAY);
    MultiLegStageResult.Rejected rej =
        assertInstanceOf(MultiLegStageResult.Rejected.class, mlm.stage(request));
    assertEquals("EMS-ORD-4003", rej.rejectCode());
  }

  @Test
  void stage_unknownInstrument_rejectedAndPersistedTerminal() {
    MultiLegOrderRequest request =
        new MultiLegOrderRequest(
            "req-1",
            sessionId,
            "CL-req-1",
            MultiLegKind.SPREAD,
            ExecutionMode.LEGS_INDEPENDENT,
            List.of(
                new LegRequest(1, FIGI_SPOT, BUY, 100, null, VENUE),
                new LegRequest(-1, "BBG000UNKNOWN", SELL, 100, null, VENUE)),
            null,
            null,
            "acc-1",
            TIF_DAY);
    MultiLegStageResult.Rejected rej =
        assertInstanceOf(MultiLegStageResult.Rejected.class, mlm.stage(request));
    assertTrue(rej.rejectCode().startsWith("EMS-"));
    // Validator-failed packages are persisted in REJECTED for the audit trail.
    MultiLegOrder order = mlm.findOrder(rej.orderId()).orElseThrow();
    assertEquals(MultiLegFsmState.REJECTED, order.fsmState());
  }

  // --- dispatch() ---

  @Test
  void dispatch_allOrNone_routesAllLegs() {
    MultiLegOrder order = stagePackage(swapRequest("req-1", "PKG-1"));
    MultiLegEventResult result = mlm.dispatch(order.orderId());
    MultiLegOrder working = ((MultiLegEventResult.Applied) result).order();
    assertEquals(MultiLegFsmState.LEGS_WORKING, working.fsmState());
    assertTrue(working.legs().stream().allMatch(l -> l.routeId() != null));
    assertTrue(working.legs().stream().allMatch(l -> l.state() == LegState.ROUTING));
  }

  @Test
  void dispatch_sequenced_routesOnlyFirstLeg() {
    MultiLegOrder order = stagePackage(sequencedRequest("req-1"));
    MultiLegOrder working = applied(mlm.dispatch(order.orderId()));
    assertEquals(MultiLegFsmState.LEGS_WORKING, working.fsmState());
    assertNotNull(working.legs().get(0).routeId());
    assertNull(working.legs().get(1).routeId());
    assertEquals(LegState.PENDING, working.legs().get(1).state());
  }

  @Test
  void dispatch_whenNotReady_rejected5002() {
    MultiLegOrder order = stagePackage(swapRequest("req-1", "PKG-1"));
    mlm.dispatch(order.orderId());
    MultiLegEventResult second = mlm.dispatch(order.orderId());
    MultiLegEventResult.Rejected rej = assertInstanceOf(MultiLegEventResult.Rejected.class, second);
    assertEquals("EMS-ORD-5002", rej.rejectCode());
  }

  @Test
  void dispatch_unknownOrder_rejected5001() {
    MultiLegEventResult.Rejected rej =
        assertInstanceOf(MultiLegEventResult.Rejected.class, mlm.dispatch("NOPE"));
    assertEquals("EMS-ORD-5001", rej.rejectCode());
  }

  // --- fills ---

  @Test
  void legFilled_allLegs_parentFilled() {
    MultiLegOrder order = dispatched(swapRequest("req-1", "PKG-1"));
    ackAll(order);
    mlm.legFilled(order.orderId(), legId(order, 0), 1_000_000, 105_000L, "X-1");
    MultiLegOrder done =
        applied(mlm.legFilled(order.orderId(), legId(order, 1), 1_000_000, 106_000L, "X-2"));
    assertEquals(MultiLegFsmState.FILLED, done.fsmState());
    assertEquals(2, done.fsmContext().legsFilled());
    assertTrue(done.legs().stream().allMatch(l -> l.state() == LegState.FILLED));
  }

  @Test
  void legPartiallyFilled_parentStaysWorking() {
    MultiLegOrder order = dispatched(swapRequest("req-1", "PKG-1"));
    ackAll(order);
    MultiLegOrder working =
        applied(mlm.legPartiallyFilled(order.orderId(), legId(order, 0), 400_000, 105_000L, "X-1"));
    assertEquals(MultiLegFsmState.LEGS_WORKING, working.fsmState());
    assertEquals(0, working.fsmContext().legsFilled());
  }

  @Test
  void sequenced_legFill_autoDispatchesNextLeg() {
    MultiLegOrder order = dispatched(sequencedRequest("req-1"));
    mlm.legAcknowledged(order.orderId(), legId(order, 0));
    MultiLegOrder afterFirst =
        applied(mlm.legFilled(order.orderId(), legId(order, 0), 1_000_000, 105_000L, "X-1"));
    assertEquals(MultiLegFsmState.LEGS_WORKING, afterFirst.fsmState());
    assertNotNull(afterFirst.legs().get(1).routeId(), "next leg auto-dispatched on fill");
    mlm.legAcknowledged(order.orderId(), legId(afterFirst, 1));
    MultiLegOrder done =
        applied(mlm.legFilled(order.orderId(), legId(afterFirst, 1), 1_000_000, 106_000L, "X-2"));
    assertEquals(MultiLegFsmState.FILLED, done.fsmState());
  }

  // --- rejections per mode ---

  @Test
  void sequenced_legRejected_haltsRemainingLegs() {
    MultiLegOrder order = dispatched(sequencedRequest("req-1"));
    MultiLegOrder rejected = applied(mlm.legRejected(order.orderId(), legId(order, 0)));
    assertEquals(MultiLegFsmState.REJECTED, rejected.fsmState());
    assertNull(rejected.legs().get(1).routeId(), "halted leg must never be dispatched");
  }

  @Test
  void allOrNone_legRejected_cascadeCancelsSiblingRoutes() {
    MultiLegOrder order = dispatched(swapRequest("req-1", "PKG-1"));
    mlm.legAcknowledged(order.orderId(), legId(order, 1));
    MultiLegOrder rejected = applied(mlm.legRejected(order.orderId(), legId(order, 0)));
    assertEquals(MultiLegFsmState.REJECTED, rejected.fsmState());
    Route sibling = router.findRoute(rejected.legs().get(1).routeId()).orElseThrow();
    assertEquals(RouteFsmState.PENDING_CANCEL_AT_VENUE, sibling.fsmState());
  }

  @Test
  void legsIndependent_mixedOutcome_partiallyFilled() {
    MultiLegOrder order = dispatched(independentRequest("req-1"));
    mlm.legRejected(order.orderId(), legId(order, 0));
    mlm.legAcknowledged(order.orderId(), legId(order, 1));
    MultiLegOrder done =
        applied(mlm.legFilled(order.orderId(), legId(order, 1), 100, 105_000L, "X-1"));
    assertEquals(MultiLegFsmState.PARTIALLY_FILLED, done.fsmState());
    assertEquals(LegState.REJECTED, done.legs().get(0).state());
    assertEquals(LegState.FILLED, done.legs().get(1).state());
  }

  @Test
  void legsIndependent_allRejected_canceled() {
    MultiLegOrder order = dispatched(independentRequest("req-1"));
    mlm.legRejected(order.orderId(), legId(order, 0));
    MultiLegOrder done = applied(mlm.legRejected(order.orderId(), legId(order, 1)));
    assertEquals(MultiLegFsmState.CANCELED, done.fsmState());
  }

  // --- package cancel ---

  @Test
  void cancel_fromReady_canceledWithoutRoutes() {
    MultiLegOrder order = stagePackage(swapRequest("req-1", "PKG-1"));
    MultiLegOrder canceled = applied(mlm.cancel(order.orderId(), sessionId));
    assertEquals(MultiLegFsmState.CANCELED, canceled.fsmState());
    assertTrue(canceled.legs().stream().allMatch(l -> l.routeId() == null));
  }

  @Test
  void cancel_fromWorking_cascadesAndLateVenueConfirmIsBenign() {
    MultiLegOrder order = dispatched(swapRequest("req-1", "PKG-1"));
    ackAll(order);
    MultiLegOrder canceled = applied(mlm.cancel(order.orderId(), sessionId));
    assertEquals(MultiLegFsmState.CANCELED, canceled.fsmState());
    for (OrderLeg leg : canceled.legs()) {
      Route route = router.findRoute(leg.routeId()).orElseThrow();
      assertEquals(RouteFsmState.PENDING_CANCEL_AT_VENUE, route.fsmState());
    }
    // Venue confirms the cancel after the parent is already terminal — must not error.
    MultiLegOrder still = applied(mlm.legCanceled(order.orderId(), legId(order, 0)));
    assertEquals(MultiLegFsmState.CANCELED, still.fsmState());
    assertEquals(LegState.CANCELED, still.legs().get(0).state());
  }

  @Test
  void findOrder_unknown_returnsEmpty() {
    assertTrue(mlm.findOrder("NOPE").isEmpty());
  }

  // --- helpers ---

  private MultiLegOrderRequest swapRequest(String requestId, @Nullable String packageId) {
    return new MultiLegOrderRequest(
        requestId,
        sessionId,
        "CL-" + requestId,
        MultiLegKind.SWAP,
        ExecutionMode.ALL_OR_NONE,
        twoLegs(),
        packageId,
        null,
        "acc-1",
        TIF_DAY);
  }

  private MultiLegOrderRequest sequencedRequest(String requestId) {
    return new MultiLegOrderRequest(
        requestId,
        sessionId,
        "CL-" + requestId,
        MultiLegKind.SWAP,
        ExecutionMode.SEQUENCED,
        twoLegs(),
        null,
        "spot_first",
        "acc-1",
        TIF_DAY);
  }

  private MultiLegOrderRequest independentRequest(String requestId) {
    return new MultiLegOrderRequest(
        requestId,
        sessionId,
        "CL-" + requestId,
        MultiLegKind.PT,
        ExecutionMode.LEGS_INDEPENDENT,
        List.of(
            new LegRequest(1, FIGI_SPOT, BUY, 100, null, VENUE),
            new LegRequest(1, FIGI_FWD, BUY, 100, null, VENUE_B)),
        null,
        null,
        "acc-1",
        TIF_DAY);
  }

  private List<LegRequest> twoLegs() {
    return List.of(
        new LegRequest(1, FIGI_SPOT, BUY, 1_000_000, null, VENUE),
        new LegRequest(-1, FIGI_FWD, SELL, 1_000_000, null, VENUE));
  }

  private MultiLegOrder stagePackage(MultiLegOrderRequest request) {
    return ((MultiLegStageResult.Staged) mlm.stage(request)).order();
  }

  private MultiLegOrder dispatched(MultiLegOrderRequest request) {
    MultiLegOrder order = stagePackage(request);
    return applied(mlm.dispatch(order.orderId()));
  }

  private MultiLegOrder applied(MultiLegEventResult result) {
    return ((MultiLegEventResult.Applied) result).order();
  }

  private String legId(MultiLegOrder order, int index) {
    return order.legs().get(index).legId();
  }

  private void ackAll(MultiLegOrder order) {
    for (OrderLeg leg : order.legs()) {
      mlm.legAcknowledged(order.orderId(), leg.legId());
    }
  }

  private static InstrumentCore activeInstrument(String figi) {
    return new InstrumentCore(
        figi,
        "IID-" + figi,
        null,
        null,
        AssetClass.EQUITY,
        InstrumentType.COMMON_STOCK,
        "Test Instrument",
        "Test Issuer",
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
