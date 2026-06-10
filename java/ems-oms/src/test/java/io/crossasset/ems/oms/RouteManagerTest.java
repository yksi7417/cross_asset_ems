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
import io.crossasset.ems.fsm.generated.OrderFsmState;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link InMemoryRouteManager}. Covers route creation, venue ack/reject, fills, cancel,
 * and replace flows. Per arch-router-layer.md, task 7.2.
 */
class RouteManagerTest {

  private static final String FIRM = "firm-a";
  private static final String DESK = "desk-1";
  private static final String USER = "trader-1";
  private static final String TOKEN = "tok-trader-1";
  private static final String FIGI = "BBG000BLNNH6";
  private static final String VENUE = "XNAS";
  private static final int SIDE_BUY = 1;
  private static final int TIF_DAY = 0;

  private StagedOrderManager som;
  private InMemoryRouteManager router;
  private InMemorySecurityMasterService secMaster;
  private long sessionId;

  @BeforeEach
  void setUp() {
    InMemoryAaaService aaaService = new InMemoryAaaService(new InMemoryAaaEventLog());
    secMaster = new InMemorySecurityMasterService();
    ValidatorPipeline pipeline = new LayeredValidatorPipeline(aaaService, secMaster, null);
    som = new InMemoryStagedOrderManager(pipeline);
    router = new InMemoryRouteManager(som);

    aaaService.registerCredential(TOKEN, FIRM, DESK, USER, Set.of());
    LogonOutcome outcome = aaaService.logon(LogonCredentials.fresh(CredentialKind.TOKEN, TOKEN));
    sessionId = ((LogonOutcome.Accepted) outcome).session().sessionId();

    publishActiveInstrument(FIGI, secMaster);
  }

  // --- route() rejection paths ---

  @Test
  void route_orderNotFound_returnsRte4001() {
    RouteResult result = router.route(new RouteRequest("req-1", "UNKNOWN", VENUE, 100, null, null));
    RouteResult.Rejected rej = assertInstanceOf(RouteResult.Rejected.class, result);
    assertEquals("EMS-RTE-4001", rej.rejectCode());
  }

  @Test
  void route_orderNotReady_returnsRte4002() {
    StagedOrder order = stageOrder("req-1", 100);
    // Order is in NEW sub-state, not READY
    RouteResult result =
        router.route(new RouteRequest("req-r1", order.orderId(), VENUE, 100, null, null));
    RouteResult.Rejected rej = assertInstanceOf(RouteResult.Rejected.class, result);
    assertEquals("EMS-RTE-4002", rej.rejectCode());
  }

  @Test
  void route_qtyExceedsRemaining_returnsRte4003() {
    StagedOrder order = stageAndReadyOrder("req-1", 100);
    RouteResult result =
        router.route(new RouteRequest("req-r1", order.orderId(), VENUE, 101, null, null));
    RouteResult.Rejected rej = assertInstanceOf(RouteResult.Rejected.class, result);
    assertEquals("EMS-RTE-4003", rej.rejectCode());
  }

  @Test
  void route_clOrdIdCollision_returnsRte2005() {
    StagedOrder order = stageAndReadyOrder("req-1", 200);
    RouteResult r1 =
        router.route(new RouteRequest("req-r1", order.orderId(), VENUE, 100, null, "CL-001"));
    assertInstanceOf(RouteResult.Routed.class, r1);
    // Second route with same clOrdId
    RouteResult r2 =
        router.route(new RouteRequest("req-r2", order.orderId(), VENUE, 50, null, "CL-001"));
    RouteResult.Rejected rej = assertInstanceOf(RouteResult.Rejected.class, r2);
    assertEquals("EMS-RTE-2005", rej.rejectCode());
  }

  // --- route() golden path ---

  @Test
  void route_goldenPath_routeInSentState() {
    StagedOrder order = stageAndReadyOrder("req-1", 100);
    RouteResult result =
        router.route(new RouteRequest("req-r1", order.orderId(), VENUE, 100, null, null));
    RouteResult.Routed routed = assertInstanceOf(RouteResult.Routed.class, result);
    Route route = routed.route();
    assertEquals(RouteFsmState.SENT, route.fsmState());
    assertEquals(order.orderId(), route.orderId());
    assertEquals(VENUE, route.fsmContext().venueMic());
    assertEquals(100L, route.fsmContext().routeQty());
    assertEquals(100L, route.fsmContext().leavesQty());
    assertEquals(0L, route.fsmContext().cumQty());
    assertFalse(route.isTerminal());
  }

  @Test
  void route_flipsOrderSubStateToRouting() {
    StagedOrder order = stageAndReadyOrder("req-1", 100);
    router.route(new RouteRequest("req-r1", order.orderId(), VENUE, 100, null, null));
    StagedOrder updated = som.findOrder(order.orderId()).orElseThrow();
    assertEquals(OrderSubState.ROUTING, updated.subState());
  }

  @Test
  void route_splitOrder_twoRoutesSumToLeavesQty() {
    StagedOrder order = stageAndReadyOrder("req-1", 200);
    RouteResult r1 =
        router.route(new RouteRequest("req-r1", order.orderId(), VENUE, 100, null, null));
    RouteResult r2 =
        router.route(new RouteRequest("req-r2", order.orderId(), "XNYS", 100, null, null));
    assertInstanceOf(RouteResult.Routed.class, r1);
    assertInstanceOf(RouteResult.Routed.class, r2);
    // Third route of any size should fail
    RouteResult r3 =
        router.route(new RouteRequest("req-r3", order.orderId(), VENUE, 1, null, null));
    assertInstanceOf(RouteResult.Rejected.class, r3);
  }

  // --- acknowledgeRoute() ---

  @Test
  void acknowledgeRoute_sentToWorking() {
    String routeId = routeAndGetId("req-1", 100);
    RouteEventResult result = router.acknowledgeRoute(routeId);
    RouteEventResult.Applied applied = assertInstanceOf(RouteEventResult.Applied.class, result);
    assertEquals(RouteFsmState.WORKING, applied.route().fsmState());
  }

  @Test
  void acknowledgeRoute_notFound_returnsRte5001() {
    RouteEventResult result = router.acknowledgeRoute("UNKNOWN");
    RouteEventResult.Rejected rej = assertInstanceOf(RouteEventResult.Rejected.class, result);
    assertEquals("EMS-RTE-5001", rej.rejectCode());
  }

  // --- pendingNewAtVenue() ---

  @Test
  void pendingNewAtVenue_sentToPendingNew_thenAckToWorking() {
    String routeId = routeAndGetId("req-1", 100);
    RouteEventResult r1 = router.pendingNewAtVenue(routeId);
    assertInstanceOf(RouteEventResult.Applied.class, r1);
    assertEquals(
        RouteFsmState.PENDING_NEW_AT_VENUE, router.findRoute(routeId).orElseThrow().fsmState());
    RouteEventResult r2 = router.acknowledgeRoute(routeId);
    assertInstanceOf(RouteEventResult.Applied.class, r2);
    assertEquals(RouteFsmState.WORKING, router.findRoute(routeId).orElseThrow().fsmState());
  }

  // --- rejectRoute() ---

  @Test
  void rejectRoute_sentToRejected_terminalState() {
    String routeId = routeAndGetId("req-1", 100);
    RouteEventResult result = router.rejectRoute(routeId);
    RouteEventResult.Applied applied = assertInstanceOf(RouteEventResult.Applied.class, result);
    assertEquals(RouteFsmState.REJECTED, applied.route().fsmState());
    assertTrue(applied.route().isTerminal());
  }

  @Test
  void rejectRoute_cannotEventOnTerminal_returnsRte5002() {
    String routeId = routeAndGetId("req-1", 100);
    router.rejectRoute(routeId);
    RouteEventResult result = router.rejectRoute(routeId);
    RouteEventResult.Rejected rej = assertInstanceOf(RouteEventResult.Rejected.class, result);
    assertEquals("EMS-RTE-5002", rej.rejectCode());
  }

  // --- partialFill() ---

  @Test
  void partialFill_workingToPartiallyFilled_updatesQty() {
    String routeId = routeAndGetId("req-1", 100);
    router.acknowledgeRoute(routeId);
    RouteEventResult result = router.partialFill(routeId, 40L, 5000L, "exec-001");
    RouteEventResult.Applied applied = assertInstanceOf(RouteEventResult.Applied.class, result);
    Route route = applied.route();
    assertEquals(RouteFsmState.PARTIALLY_FILLED, route.fsmState());
    assertEquals(40L, route.fsmContext().cumQty());
    assertEquals(60L, route.fsmContext().leavesQty());
  }

  @Test
  void partialFill_propagatesToOrderFsm() {
    StagedOrder order = stageAndReadyOrder("req-1", 100);
    String routeId = routeAndGetId(order, 100);
    router.acknowledgeRoute(routeId);
    router.partialFill(routeId, 40L, 5000L, "exec-001");
    StagedOrder updatedOrder = som.findOrder(order.orderId()).orElseThrow();
    assertEquals(OrderFsmState.PARTIALLY_FILLED, updatedOrder.fsmState());
    assertEquals(40L, updatedOrder.fsmContext().cumQty());
    assertEquals(60L, updatedOrder.fsmContext().leavesQty());
  }

  // --- fullFill() ---

  @Test
  void fullFill_workingToFilled_terminalState() {
    String routeId = routeAndGetId("req-1", 100);
    router.acknowledgeRoute(routeId);
    RouteEventResult result = router.fullFill(routeId, 100L, 5000L, "exec-002");
    RouteEventResult.Applied applied = assertInstanceOf(RouteEventResult.Applied.class, result);
    Route route = applied.route();
    assertEquals(RouteFsmState.FILLED, route.fsmState());
    assertEquals(100L, route.fsmContext().cumQty());
    assertEquals(0L, route.fsmContext().leavesQty());
    assertTrue(route.isTerminal());
  }

  @Test
  void fullFill_propagatesToOrderFsm_orderFilled() {
    StagedOrder order = stageAndReadyOrder("req-1", 100);
    String routeId = routeAndGetId(order, 100);
    router.acknowledgeRoute(routeId);
    router.fullFill(routeId, 100L, 5000L, "exec-002");
    StagedOrder updatedOrder = som.findOrder(order.orderId()).orElseThrow();
    assertEquals(OrderFsmState.FILLED, updatedOrder.fsmState());
    assertEquals(100L, updatedOrder.fsmContext().cumQty());
    assertEquals(0L, updatedOrder.fsmContext().leavesQty());
  }

  @Test
  void partialThenFull_orderTransitionsThroughPartiallyFilledToFilled() {
    StagedOrder order = stageAndReadyOrder("req-1", 100);
    String routeId = routeAndGetId(order, 100);
    router.acknowledgeRoute(routeId);
    router.partialFill(routeId, 60L, 5000L, "exec-001");
    assertEquals(
        OrderFsmState.PARTIALLY_FILLED, som.findOrder(order.orderId()).orElseThrow().fsmState());
    router.fullFill(routeId, 40L, 5000L, "exec-002");
    StagedOrder finalOrder = som.findOrder(order.orderId()).orElseThrow();
    assertEquals(OrderFsmState.FILLED, finalOrder.fsmState());
    assertEquals(100L, finalOrder.fsmContext().cumQty());
  }

  // --- cancelRoute() / canceledByVenue() ---

  @Test
  void cancelRoute_workingToPendingCancel() {
    String routeId = routeAndGetId("req-1", 100);
    router.acknowledgeRoute(routeId);
    RouteEventResult result = router.cancelRoute(routeId);
    RouteEventResult.Applied applied = assertInstanceOf(RouteEventResult.Applied.class, result);
    assertEquals(RouteFsmState.PENDING_CANCEL_AT_VENUE, applied.route().fsmState());
    assertEquals("0", applied.route().fsmContext().preCancelStatus());
  }

  @Test
  void canceledByVenue_pendingCancelToCanceled() {
    String routeId = routeAndGetId("req-1", 100);
    router.acknowledgeRoute(routeId);
    router.cancelRoute(routeId);
    RouteEventResult result = router.canceledByVenue(routeId);
    RouteEventResult.Applied applied = assertInstanceOf(RouteEventResult.Applied.class, result);
    assertEquals(RouteFsmState.CANCELED, applied.route().fsmState());
    assertTrue(applied.route().isTerminal());
  }

  @Test
  void cancelRejectedByVenue_fromWorking_returnsToWorking() {
    String routeId = routeAndGetId("req-1", 100);
    router.acknowledgeRoute(routeId);
    router.cancelRoute(routeId);
    RouteEventResult result = router.cancelRejectedByVenue(routeId, 0);
    RouteEventResult.Applied applied = assertInstanceOf(RouteEventResult.Applied.class, result);
    assertEquals(RouteFsmState.WORKING, applied.route().fsmState());
  }

  @Test
  void cancelRejectedByVenue_fromPartiallyFilled_returnsToPartiallyFilled() {
    String routeId = routeAndGetId("req-1", 100);
    router.acknowledgeRoute(routeId);
    router.partialFill(routeId, 40L, 5000L, "exec-001");
    router.cancelRoute(routeId);
    assertEquals("1", router.findRoute(routeId).orElseThrow().fsmContext().preCancelStatus());
    RouteEventResult result = router.cancelRejectedByVenue(routeId, 0);
    RouteEventResult.Applied applied = assertInstanceOf(RouteEventResult.Applied.class, result);
    assertEquals(RouteFsmState.PARTIALLY_FILLED, applied.route().fsmState());
  }

  // --- requestReplace() / replacedByVenue() ---

  @Test
  void requestReplace_workingToPendingReplace() {
    String routeId = routeAndGetId("req-1", 100);
    router.acknowledgeRoute(routeId);
    RouteEventResult result = router.requestReplace(routeId, "CL-002", 80L, null);
    RouteEventResult.Applied applied = assertInstanceOf(RouteEventResult.Applied.class, result);
    assertEquals(RouteFsmState.PENDING_REPLACE_AT_VENUE, applied.route().fsmState());
  }

  @Test
  void replacedByVenue_returnsToWorking() {
    String routeId = routeAndGetId("req-1", 100);
    router.acknowledgeRoute(routeId);
    router.requestReplace(routeId, "CL-002", 80L, null);
    RouteEventResult result = router.replacedByVenue(routeId);
    RouteEventResult.Applied applied = assertInstanceOf(RouteEventResult.Applied.class, result);
    assertEquals(RouteFsmState.WORKING, applied.route().fsmState());
  }

  @Test
  void replaceRejectedByVenue_returnsToWorking() {
    String routeId = routeAndGetId("req-1", 100);
    router.acknowledgeRoute(routeId);
    router.requestReplace(routeId, "CL-002", 80L, null);
    RouteEventResult result = router.replaceRejectedByVenue(routeId, 5);
    RouteEventResult.Applied applied = assertInstanceOf(RouteEventResult.Applied.class, result);
    assertEquals(RouteFsmState.WORKING, applied.route().fsmState());
  }

  // --- findRoute / findRoutesForOrder ---

  @Test
  void findRoute_unknown_returnsEmpty() {
    assertTrue(router.findRoute("UNKNOWN").isEmpty());
  }

  @Test
  void findRoutesForOrder_returnsAllRoutes() {
    StagedOrder order = stageAndReadyOrder("req-1", 200);
    routeAndGetId(order, 100);
    routeAndGetId(order, 100);
    List<Route> found = router.findRoutesForOrder(order.orderId());
    assertEquals(2, found.size());
    assertTrue(found.stream().allMatch(r -> r.orderId().equals(order.orderId())));
  }

  // --- helpers ---

  private StagedOrder stageOrder(String requestId, long qty) {
    StageResult result =
        som.stage(
            new OrderRequest(
                requestId,
                sessionId,
                "CL-" + requestId,
                FIGI,
                SIDE_BUY,
                qty,
                null,
                "acc-1",
                TIF_DAY));
    return ((StageResult.Accepted) result).order();
  }

  private StagedOrder stageAndReadyOrder(String requestId, long qty) {
    StagedOrder order = stageOrder(requestId, qty);
    MarkReadyResult mr = som.markReady(order.orderId(), sessionId);
    return ((MarkReadyResult.Ready) mr).order();
  }

  private String routeAndGetId(String requestId, long qty) {
    StagedOrder order = stageAndReadyOrder(requestId, qty);
    return routeAndGetId(order, qty);
  }

  private String routeAndGetId(StagedOrder order, long qty) {
    RouteResult result =
        router.route(
            new RouteRequest("req-r-" + order.orderId(), order.orderId(), VENUE, qty, null, null));
    return ((RouteResult.Routed) result).route().routeId();
  }

  private static void publishActiveInstrument(
      String figi, InMemorySecurityMasterService secMaster) {
    InstrumentCore core =
        new InstrumentCore(
            figi,
            "IID-001",
            null,
            null,
            AssetClass.EQUITY,
            InstrumentType.COMMON_STOCK,
            "Test Stock",
            "Test Stock Inc.",
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
    SecurityMasterSnapshot snap =
        SecurityMasterSnapshot.EMPTY.apply(
            new SecurityMasterEvent.InstrumentCreated(new InstrumentVersioned(core, null), 1L));
    secMaster.publish(snap);
  }
}
