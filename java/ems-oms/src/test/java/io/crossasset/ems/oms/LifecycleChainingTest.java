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
 * End-to-end lifecycle chaining tests covering the integration of {@link StagedOrderManager} and
 * {@link RouteManager}. Exercises fill propagation (partial + full), cancel cascades from order to
 * routes, route rejection, and multi-route scenarios. Per arch-order-route-lifecycle.md, task 7.8.
 */
class LifecycleChainingTest {

  private static final String FIGI = "BBG000BLNNH6";
  private static final String VENUE = "XNAS";
  private static final int SIDE_BUY = 1;
  private static final int TIF_DAY = 0;
  private static final String TOKEN = "tok-trader-1";
  private static final String FIRM = "firm-a";
  private static final String DESK = "desk-1";
  private static final String USER = "trader-1";

  private StagedOrderManager som;
  private InMemoryRouteManager router;
  private long sessionId;

  @BeforeEach
  void setUp() {
    InMemoryAaaService aaaService = new InMemoryAaaService(new InMemoryAaaEventLog());
    InMemorySecurityMasterService secMaster = new InMemorySecurityMasterService();
    ValidatorPipeline pipeline = new LayeredValidatorPipeline(aaaService, secMaster, null);
    som = new InMemoryStagedOrderManager(pipeline);
    router = new InMemoryRouteManager(som);

    aaaService.registerCredential(TOKEN, FIRM, DESK, USER, Set.of());
    LogonOutcome outcome = aaaService.logon(LogonCredentials.fresh(CredentialKind.TOKEN, TOKEN));
    sessionId = ((LogonOutcome.Accepted) outcome).session().sessionId();

    publishInstrument(secMaster);
  }

  // --- Full fill lifecycle ---

  @Test
  void fullFillLifecycle_orderEndsInFilled() {
    // Stage → ready → route → ack → full fill
    String orderId = stageAndReady(100L);
    String routeId = route(orderId, 100L);

    router.acknowledgeRoute(routeId);
    router.fullFill(routeId, 100L, 5000L, "exec-001");

    StagedOrder order = som.findOrder(orderId).orElseThrow();
    assertEquals(OrderFsmState.FILLED, order.fsmState());
    assertEquals(100L, order.fsmContext().cumQty());
    assertEquals(0L, order.fsmContext().leavesQty());
    assertEquals(RouteFsmState.FILLED, router.findRoute(routeId).orElseThrow().fsmState());
  }

  @Test
  void partialThenFullFill_orderTransitionsCorrectly() {
    String orderId = stageAndReady(200L);
    String routeId = route(orderId, 200L);
    router.acknowledgeRoute(routeId);

    router.partialFill(routeId, 80L, 5010L, "exec-001");
    assertEquals(OrderFsmState.PARTIALLY_FILLED, som.findOrder(orderId).orElseThrow().fsmState());
    assertEquals(80L, som.findOrder(orderId).orElseThrow().fsmContext().cumQty());

    router.partialFill(routeId, 70L, 5012L, "exec-002");
    assertEquals(150L, som.findOrder(orderId).orElseThrow().fsmContext().cumQty());

    router.fullFill(routeId, 50L, 5015L, "exec-003");
    StagedOrder order = som.findOrder(orderId).orElseThrow();
    assertEquals(OrderFsmState.FILLED, order.fsmState());
    assertEquals(200L, order.fsmContext().cumQty());
    assertEquals(0L, order.fsmContext().leavesQty());
  }

  // --- Cancel cascade ---

  @Test
  void cancelCascade_orderCancelThenRouteCancel_allTerminal() {
    String orderId = stageAndReady(100L);
    String routeId = route(orderId, 100L);
    router.acknowledgeRoute(routeId);

    // 1. Client cancels the order
    CancelResult cancelResult = som.cancel(orderId, sessionId);
    assertInstanceOf(CancelResult.Canceled.class, cancelResult);
    assertEquals(OrderFsmState.CANCELED, som.findOrder(orderId).orElseThrow().fsmState());

    // 2. Orchestrator cascades cancel to all open routes
    List<RouteEventResult> cascadeResults = router.cascadeOrderCancel(orderId);
    assertEquals(1, cascadeResults.size());
    assertInstanceOf(RouteEventResult.Applied.class, cascadeResults.get(0));
    assertEquals(
        RouteFsmState.PENDING_CANCEL_AT_VENUE, router.findRoute(routeId).orElseThrow().fsmState());

    // 3. Venue confirms cancel
    router.canceledByVenue(routeId);
    assertEquals(RouteFsmState.CANCELED, router.findRoute(routeId).orElseThrow().fsmState());
    assertTrue(router.findRoute(routeId).orElseThrow().isTerminal());
  }

  @Test
  void cancelCascade_partiallyFilledRoutes_cancelRequestedPerRoute() {
    String orderId = stageAndReady(200L);
    String route1 = route(orderId, 100L);
    String route2 = route(orderId, 100L);
    router.acknowledgeRoute(route1);
    router.acknowledgeRoute(route2);

    router.partialFill(route1, 50L, 5000L, "exec-001");

    som.cancel(orderId, sessionId);
    List<RouteEventResult> results = router.cascadeOrderCancel(orderId);

    assertEquals(2, results.size());
    assertTrue(results.stream().allMatch(r -> r instanceof RouteEventResult.Applied));
    assertEquals(
        RouteFsmState.PENDING_CANCEL_AT_VENUE, router.findRoute(route1).orElseThrow().fsmState());
    assertEquals(
        RouteFsmState.PENDING_CANCEL_AT_VENUE, router.findRoute(route2).orElseThrow().fsmState());
  }

  @Test
  void cancelCascade_skipsTerminalRoutes() {
    String orderId = stageAndReady(200L);
    String route1 = route(orderId, 100L);
    String route2 = route(orderId, 100L);
    router.acknowledgeRoute(route1);
    router.acknowledgeRoute(route2);

    // route1 fills completely before order cancel
    router.fullFill(route1, 100L, 5000L, "exec-001");
    assertEquals(RouteFsmState.FILLED, router.findRoute(route1).orElseThrow().fsmState());

    // Order cancel (forced from partially filled via cancel)
    CancelResult cancelResult = som.cancel(orderId, sessionId);
    assertInstanceOf(CancelResult.Canceled.class, cancelResult);

    // Cascade should only hit route2 (route1 is terminal)
    List<RouteEventResult> results = router.cascadeOrderCancel(orderId);
    assertEquals(1, results.size());
    assertInstanceOf(RouteEventResult.Applied.class, results.get(0));
    Route affectedRoute = ((RouteEventResult.Applied) results.get(0)).route();
    assertEquals(RouteFsmState.PENDING_CANCEL_AT_VENUE, affectedRoute.fsmState());
  }

  // --- Route rejection scenarios ---

  @Test
  void routeRejected_orderRemainsRoutingForReRoute() {
    String orderId = stageAndReady(100L);
    String routeId = route(orderId, 100L);

    // Venue rejects the route
    router.rejectRoute(routeId);
    assertEquals(RouteFsmState.REJECTED, router.findRoute(routeId).orElseThrow().fsmState());

    // Order remains in ROUTING sub-state (order FSM state stays NEW — ValidationFailed is no-op)
    StagedOrder order = som.findOrder(orderId).orElseThrow();
    assertEquals(OrderSubState.ROUTING, order.subState());
    assertEquals(OrderFsmState.NEW, order.fsmState());
  }

  @Test
  void routeRejected_canSendNewRouteOnSameOrder() {
    String orderId = stageAndReady(100L);
    String route1 = route(orderId, 100L);
    router.rejectRoute(route1);

    // Re-route on the same order (still ROUTING sub-state)
    RouteResult result = router.route(new RouteRequest("req-r2", orderId, VENUE, 100L, null, null));
    assertInstanceOf(RouteResult.Routed.class, result);
    String route2 = ((RouteResult.Routed) result).route().routeId();
    router.acknowledgeRoute(route2);
    assertEquals(RouteFsmState.WORKING, router.findRoute(route2).orElseThrow().fsmState());
  }

  // --- Multi-route fill aggregation ---

  @Test
  void multiRoute_fillsAggregateToOrder() {
    String orderId = stageAndReady(300L);
    String route1 = route(orderId, 150L);
    String route2 = route(orderId, 150L);
    router.acknowledgeRoute(route1);
    router.acknowledgeRoute(route2);

    router.partialFill(route1, 100L, 5000L, "e1");
    assertEquals(100L, som.findOrder(orderId).orElseThrow().fsmContext().cumQty());

    router.fullFill(route2, 150L, 5010L, "e2");
    assertEquals(250L, som.findOrder(orderId).orElseThrow().fsmContext().cumQty());

    router.fullFill(route1, 50L, 5020L, "e3");
    StagedOrder order = som.findOrder(orderId).orElseThrow();
    assertEquals(OrderFsmState.FILLED, order.fsmState());
    assertEquals(300L, order.fsmContext().cumQty());
  }

  // --- Cancel-reject restores working state ---

  @Test
  void cancelReject_routeRestoresToWorking() {
    String orderId = stageAndReady(100L);
    String routeId = route(orderId, 100L);
    router.acknowledgeRoute(routeId);
    router.cancelRoute(routeId);

    assertEquals(
        RouteFsmState.PENDING_CANCEL_AT_VENUE, router.findRoute(routeId).orElseThrow().fsmState());

    router.cancelRejectedByVenue(routeId, 0);
    assertEquals(RouteFsmState.WORKING, router.findRoute(routeId).orElseThrow().fsmState());

    // Can still fill after cancel reject
    router.fullFill(routeId, 100L, 5000L, "exec-fill");
    assertEquals(OrderFsmState.FILLED, som.findOrder(orderId).orElseThrow().fsmState());
  }

  // --- Replace flow ---

  @Test
  void replaceRoute_workingThroughReplaceToFill() {
    String orderId = stageAndReady(100L);
    String routeId = route(orderId, 100L);
    router.acknowledgeRoute(routeId);

    router.requestReplace(routeId, "CL-002", 80L, null);
    assertEquals(
        RouteFsmState.PENDING_REPLACE_AT_VENUE, router.findRoute(routeId).orElseThrow().fsmState());

    router.replacedByVenue(routeId);
    assertEquals(RouteFsmState.WORKING, router.findRoute(routeId).orElseThrow().fsmState());

    router.fullFill(routeId, 100L, 5000L, "exec-fill");
    assertEquals(OrderFsmState.FILLED, som.findOrder(orderId).orElseThrow().fsmState());
  }

  // --- Helpers ---

  private String stageAndReady(long qty) {
    String requestId = "req-" + System.nanoTime();
    OrderRequest req =
        new OrderRequest(
            requestId, sessionId, "CL-" + requestId, FIGI, SIDE_BUY, qty, null, "acc-1", TIF_DAY);
    StageResult staged = som.stage(req);
    StagedOrder order = ((StageResult.Accepted) staged).order();
    MarkReadyResult mr = som.markReady(order.orderId(), sessionId);
    return ((MarkReadyResult.Ready) mr).order().orderId();
  }

  private String route(String orderId, long qty) {
    RouteResult result =
        router.route(
            new RouteRequest("rreq-" + System.nanoTime(), orderId, VENUE, qty, null, null));
    return ((RouteResult.Routed) result).route().routeId();
  }

  private static void publishInstrument(InMemorySecurityMasterService secMaster) {
    InstrumentCore core =
        new InstrumentCore(
            "BBG000BLNNH6",
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
