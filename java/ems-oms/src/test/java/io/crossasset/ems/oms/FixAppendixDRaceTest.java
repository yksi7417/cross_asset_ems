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
import io.crossasset.ems.fsm.generated.RouteFsmContext;
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
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * FIX Appendix D race-condition golden tests (task 7.7).
 *
 * <p>Appendix D of the FIX spec — the <em>Order State Change Matrices</em> — catalogues the races
 * that make production EMS implementation hard: a fill printing while a cancel or replace is in
 * flight, the venue's reject arriving after the order is already terminal, and the quantity
 * arithmetic that must stay consistent through it all. Per {@code arch-fix-appendix-d.md}, every
 * scenario here has been a real-world outage at some firm.
 *
 * <p>These are <strong>golden</strong> tests: they assert the <em>correct</em> Appendix D
 * behaviour, not whatever the FSM happens to do. Task 7.7 added the three fill-vs-cancel/replace
 * transitions to the Route FSM (mirroring the Order-FSM transitions added by task 1.10) that these
 * tests lock in. Each test asserts the quantity invariant the doc centres on at every step:
 *
 * <pre>
 *   LeavesQty = RouteQty - CumQty   (working states)
 *   LeavesQty = 0                   (Filled)
 * </pre>
 *
 * and the end-state of <em>both</em> the route FSM and the parent order FSM (they can legitimately
 * be in different phases — a route-level cancel is distinct from an order-level cancel).
 *
 * <h2>Scope</h2>
 *
 * Covered (expressible through {@link RouteManager} + Order FSM propagation):
 *
 * <ul>
 *   <li>D4/D5 — full fill wins over in-flight cancel ("too late to cancel").
 *   <li>D4 — partial fill during in-flight cancel; cancel still resolvable (confirm or reject).
 *   <li>D7/D10 — full fill wins over in-flight replace.
 *   <li>D7/D10 — partial fill at prior params during in-flight replace.
 *   <li>Replace reject does not terminate the order; prior state preserved.
 *   <li>Concurrent cancel+replace — second modification refused (one-pending-modification rule).
 *   <li>Post-terminal venue messages (late cancel/replace ack) are informational, not
 *       state-reverting.
 * </ul>
 *
 * Deliberately deferred — not expressible at this layer, belongs to the FIX/venue edge (Phase 8 /
 * Phase 11): unsolicited venue cancel/restate (ExecType=4/D), trade bust/correct (ExecType=H/G),
 * PossResend duplicate-ClOrdID handling, and the late-ack-after-terminal {@code RouteAnomaly} path
 * (the manager rejects all events on terminal routes; anomaly has no terminal source state).
 *
 * <h2>Known limitation flagged for follow-up</h2>
 *
 * A partial fill while in {@code PENDING_REPLACE_AT_VENUE} moves the route to {@code
 * PARTIALLY_FILLED}, which has no {@code RouteReplaced}/{@code RouteReplaceRejected} transition —
 * so the venue's eventual replace response is then a no-transition. This asymmetry pre-dates 7.7
 * and is documented by {@link #fillDuringReplace_partial_thenReplaceResponseUnreachable()} rather
 * than fixed here, since the corrected-leaves-from-new-qty semantics belong with the replace
 * redesign.
 */
class FixAppendixDRaceTest {

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

  // ── D4/D5 — fill wins over in-flight cancel ────────────────────────────────

  @Test
  void fullFillWinsOverInflightCancel_routeAndOrderBothFilled() {
    StagedOrder order = stageAndReadyOrder("o1", 100);
    String routeId = route(order, 100);
    router.acknowledgeRoute(routeId);

    // Cancel dispatched to venue — route now PENDING_CANCEL_AT_VENUE.
    router.cancelRoute(routeId);
    assertRouteState(routeId, RouteFsmState.PENDING_CANCEL_AT_VENUE);

    // The fill prints in the same window. Per Appendix D the fill WINS.
    RouteEventResult fill = router.fullFill(routeId, 100, 5000, "x1");
    assertInstanceOf(RouteEventResult.Applied.class, fill);

    Route route = router.findRoute(routeId).orElseThrow();
    assertEquals(RouteFsmState.FILLED, route.fsmState());
    assertTrue(route.isTerminal());
    assertEquals(100, route.fsmContext().cumQty());
    assertRouteQtyInvariant(route);

    StagedOrder o = som.findOrder(order.orderId()).orElseThrow();
    assertEquals(OrderFsmState.FILLED, o.fsmState());
    assertEquals(100, o.fsmContext().cumQty());
    assertOrderQtyInvariant(order.orderId());
  }

  @Test
  void lateCancelAckAfterFill_isInformational_doesNotRevertTerminal() {
    StagedOrder order = stageAndReadyOrder("o1", 100);
    String routeId = route(order, 100);
    router.acknowledgeRoute(routeId);
    router.cancelRoute(routeId);
    router.fullFill(routeId, 100, 5000, "x1");

    // The venue's 35=9 (Too late to cancel) arrives after the route is terminal Filled.
    RouteEventResult lateReject = router.cancelRejectedByVenue(routeId, 0);
    RouteEventResult.Rejected rej = assertInstanceOf(RouteEventResult.Rejected.class, lateReject);
    assertEquals("EMS-RTE-5002", rej.rejectCode());

    // State unchanged — still terminal Filled.
    assertRouteState(routeId, RouteFsmState.FILLED);
    assertEquals(OrderFsmState.FILLED, som.findOrder(order.orderId()).orElseThrow().fsmState());
  }

  @Test
  void partialFillDuringCancel_thenCancelConfirmed_preservesFillQty() {
    StagedOrder order = stageAndReadyOrder("o1", 100);
    String routeId = route(order, 100);
    router.acknowledgeRoute(routeId);
    router.cancelRoute(routeId); // from WORKING → preCancelStatus "0"
    assertEquals("0", router.findRoute(routeId).orElseThrow().fsmContext().preCancelStatus());

    // Partial fill prints while the cancel is in flight: self-loop in PENDING_CANCEL.
    RouteEventResult fill = router.partialFill(routeId, 40, 5000, "x1");
    assertInstanceOf(RouteEventResult.Applied.class, fill);
    Route midRoute = router.findRoute(routeId).orElseThrow();
    assertEquals(RouteFsmState.PENDING_CANCEL_AT_VENUE, midRoute.fsmState());
    assertEquals(40, midRoute.fsmContext().cumQty());
    assertRouteQtyInvariant(midRoute);
    // The fill promoted pre-cancel status so a later reject restores to PARTIALLY_FILLED.
    assertEquals("1", midRoute.fsmContext().preCancelStatus());
    // Fill propagated to the order.
    assertEquals(
        OrderFsmState.PARTIALLY_FILLED, som.findOrder(order.orderId()).orElseThrow().fsmState());
    assertEquals(40, som.findOrder(order.orderId()).orElseThrow().fsmContext().cumQty());

    // The cancel still resolves: venue confirms it.
    RouteEventResult cxl = router.canceledByVenue(routeId);
    assertInstanceOf(RouteEventResult.Applied.class, cxl);
    Route done = router.findRoute(routeId).orElseThrow();
    assertEquals(RouteFsmState.CANCELED, done.fsmState());
    assertTrue(done.isTerminal());
    assertEquals(40, done.fsmContext().cumQty()); // booked fill survives the cancel
  }

  @Test
  void partialFillDuringCancel_thenCancelRejected_restoresToPartiallyFilled() {
    StagedOrder order = stageAndReadyOrder("o1", 100);
    String routeId = route(order, 100);
    router.acknowledgeRoute(routeId);
    router.cancelRoute(routeId); // WORKING → PENDING_CANCEL, preCancelStatus "0"

    router.partialFill(routeId, 40, 5000, "x1"); // self-loop, promotes preCancelStatus → "1"

    // Cancel rejected: because a fill arrived, restore to PARTIALLY_FILLED (not WORKING).
    RouteEventResult rejResult = router.cancelRejectedByVenue(routeId, 0);
    assertInstanceOf(RouteEventResult.Applied.class, rejResult);
    Route route = router.findRoute(routeId).orElseThrow();
    assertEquals(RouteFsmState.PARTIALLY_FILLED, route.fsmState());
    assertEquals(40, route.fsmContext().cumQty());
    assertRouteQtyInvariant(route);
  }

  // ── D7/D10 — fill wins over in-flight replace ──────────────────────────────

  @Test
  void fullFillWinsOverInflightReplace_routeAndOrderBothFilled() {
    StagedOrder order = stageAndReadyOrder("o1", 100);
    String routeId = route(order, 100);
    router.acknowledgeRoute(routeId);

    // Replace dispatched (qty 100 → 150) — route now PENDING_REPLACE_AT_VENUE.
    router.requestReplace(routeId, "CL-replace", 150, null);
    assertRouteState(routeId, RouteFsmState.PENDING_REPLACE_AT_VENUE);

    // Full fill at prior params completes the route. The fill WINS.
    RouteEventResult fill = router.fullFill(routeId, 100, 5000, "x1");
    assertInstanceOf(RouteEventResult.Applied.class, fill);

    Route route = router.findRoute(routeId).orElseThrow();
    assertEquals(RouteFsmState.FILLED, route.fsmState());
    assertEquals(100, route.fsmContext().cumQty());
    assertRouteQtyInvariant(route);

    StagedOrder o = som.findOrder(order.orderId()).orElseThrow();
    assertEquals(OrderFsmState.FILLED, o.fsmState());
    assertOrderQtyInvariant(order.orderId());
  }

  @Test
  void lateReplaceAckAfterFill_isInformational_doesNotRevertTerminal() {
    StagedOrder order = stageAndReadyOrder("o1", 100);
    String routeId = route(order, 100);
    router.acknowledgeRoute(routeId);
    router.requestReplace(routeId, "CL-replace", 150, null);
    router.fullFill(routeId, 100, 5000, "x1");

    // Venue's late ExecType=5 Replaced arrives after the route is terminal Filled.
    RouteEventResult lateReplace = router.replacedByVenue(routeId);
    RouteEventResult.Rejected rej = assertInstanceOf(RouteEventResult.Rejected.class, lateReplace);
    assertEquals("EMS-RTE-5002", rej.rejectCode());
    assertRouteState(routeId, RouteFsmState.FILLED);
  }

  @Test
  void partialFillDuringReplace_fillsAtPriorParams() {
    StagedOrder order = stageAndReadyOrder("o1", 100);
    String routeId = route(order, 100);
    router.acknowledgeRoute(routeId);
    router.requestReplace(routeId, "CL-replace", 150, null);

    // Fill of 30 prints at the PRIOR params (routeQty still 100) while replace pending.
    RouteEventResult fill = router.partialFill(routeId, 30, 5000, "x1");
    assertInstanceOf(RouteEventResult.Applied.class, fill);

    Route route = router.findRoute(routeId).orElseThrow();
    assertEquals(RouteFsmState.PARTIALLY_FILLED, route.fsmState());
    assertEquals(30, route.fsmContext().cumQty());
    // Fill is against the prior routeQty(100), not the requested 150.
    assertEquals(100, route.fsmContext().routeQty());
    assertRouteQtyInvariant(route);

    assertEquals(
        OrderFsmState.PARTIALLY_FILLED, som.findOrder(order.orderId()).orElseThrow().fsmState());
    assertOrderQtyInvariant(order.orderId());
  }

  /**
   * Documents the known limitation: once a partial fill moves a replacing route to
   * PARTIALLY_FILLED, the venue's eventual replace confirmation/rejection is a no-transition.
   * Flagged for the replace redesign (recompute leaves from new qty); not fixed under 7.7.
   */
  @Test
  void fillDuringReplace_partial_thenReplaceResponseUnreachable() {
    StagedOrder order = stageAndReadyOrder("o1", 100);
    String routeId = route(order, 100);
    router.acknowledgeRoute(routeId);
    router.requestReplace(routeId, "CL-replace", 150, null);
    router.partialFill(routeId, 30, 5000, "x1"); // → PARTIALLY_FILLED

    RouteEventResult replaceResp = router.replacedByVenue(routeId);
    RouteEventResult.Rejected rej = assertInstanceOf(RouteEventResult.Rejected.class, replaceResp);
    assertEquals("EMS-RTE-5002", rej.rejectCode());
  }

  // ── Replace reject / concurrent modification ───────────────────────────────

  @Test
  void replaceRejected_doesNotTerminateOrder_priorStatePreserved() {
    StagedOrder order = stageAndReadyOrder("o1", 100);
    String routeId = route(order, 100);
    router.acknowledgeRoute(routeId);
    router.requestReplace(routeId, "CL-replace", 5, null);

    // Venue rejects the replace (e.g. qty below cum, or too late). Route returns to WORKING.
    RouteEventResult rejResult = router.replaceRejectedByVenue(routeId, 0);
    assertInstanceOf(RouteEventResult.Applied.class, rejResult);

    Route route = router.findRoute(routeId).orElseThrow();
    assertEquals(RouteFsmState.WORKING, route.fsmState());
    assertFalse(route.isTerminal());
    // Original routeQty preserved — the failed replace to 5 did not take effect.
    assertEquals(100, route.fsmContext().routeQty());
    assertRouteQtyInvariant(route);
    // Order untouched by the route-level replace reject.
    assertEquals(OrderFsmState.NEW, som.findOrder(order.orderId()).orElseThrow().fsmState());
  }

  @Test
  void concurrentCancelDuringReplace_secondModificationRefused() {
    StagedOrder order = stageAndReadyOrder("o1", 100);
    String routeId = route(order, 100);
    router.acknowledgeRoute(routeId);
    router.requestReplace(routeId, "CL-replace", 150, null); // PENDING_REPLACE_AT_VENUE

    // A cancel arrives before the replace resolves — one-pending-modification rule refuses it.
    RouteEventResult cancel = router.cancelRoute(routeId);
    RouteEventResult.Rejected rej = assertInstanceOf(RouteEventResult.Rejected.class, cancel);
    assertEquals("EMS-RTE-5002", rej.rejectCode());
    // Route still pending the original replace.
    assertRouteState(routeId, RouteFsmState.PENDING_REPLACE_AT_VENUE);
  }

  // ── Quantity invariant under a multi-fill race sequence ────────────────────

  @Test
  void quantityInvariantHolds_acrossPartialThenCancelRaceThenFinalFill() {
    StagedOrder order = stageAndReadyOrder("o1", 100);
    String routeId = route(order, 100);
    router.acknowledgeRoute(routeId);

    router.partialFill(routeId, 25, 5000, "x1"); // WORKING → PARTIALLY_FILLED
    assertRouteQtyInvariant(router.findRoute(routeId).orElseThrow());
    assertOrderQtyInvariant(order.orderId());

    router.cancelRoute(routeId); // PARTIALLY_FILLED → PENDING_CANCEL, preCancelStatus "1"
    router.partialFill(routeId, 25, 5000, "x2"); // self-loop in PENDING_CANCEL
    Route mid = router.findRoute(routeId).orElseThrow();
    assertEquals(50, mid.fsmContext().cumQty());
    assertRouteQtyInvariant(mid);
    assertOrderQtyInvariant(order.orderId());

    // Cancel rejected — fill history means restore to PARTIALLY_FILLED.
    router.cancelRejectedByVenue(routeId, 0);
    assertRouteState(routeId, RouteFsmState.PARTIALLY_FILLED);

    // Final fill completes the route and the order.
    router.fullFill(routeId, 50, 5000, "x3");
    Route done = router.findRoute(routeId).orElseThrow();
    assertEquals(RouteFsmState.FILLED, done.fsmState());
    assertEquals(100, done.fsmContext().cumQty());
    assertRouteQtyInvariant(done);

    StagedOrder o = som.findOrder(order.orderId()).orElseThrow();
    assertEquals(OrderFsmState.FILLED, o.fsmState());
    assertEquals(100, o.fsmContext().cumQty());
    assertOrderQtyInvariant(order.orderId());
  }

  // ── invariant helpers ──────────────────────────────────────────────────────

  /** LeavesQty = RouteQty - CumQty in working states; LeavesQty = 0 when Filled. */
  private static void assertRouteQtyInvariant(Route r) {
    RouteFsmContext c = r.fsmContext();
    switch (r.fsmState()) {
      case FILLED -> assertEquals(0L, c.leavesQty(), "Filled route must have LeavesQty=0");
      case WORKING,
              PARTIALLY_FILLED,
              PENDING_CANCEL_AT_VENUE,
              PENDING_REPLACE_AT_VENUE,
              PENDING_NEW_AT_VENUE,
              SENT ->
          assertEquals(
              c.routeQty() - c.cumQty(),
              c.leavesQty(),
              "LeavesQty must equal RouteQty - CumQty in working state " + r.fsmState());
      default -> {
        /* CANCELED/REJECTED/EXPIRED: internal leaves reflects the canceled remainder. */
      }
    }
  }

  /** LeavesQty = OrderQty - CumQty in working states; LeavesQty = 0 when Filled. */
  private void assertOrderQtyInvariant(String orderId) {
    StagedOrder o = som.findOrder(orderId).orElseThrow();
    var c = o.fsmContext();
    if (o.fsmState() == OrderFsmState.FILLED) {
      assertEquals(0L, c.leavesQty(), "Filled order must have LeavesQty=0");
    } else {
      assertEquals(
          c.orderQty() - c.cumQty(),
          c.leavesQty(),
          "LeavesQty must equal OrderQty - CumQty in working state " + o.fsmState());
    }
  }

  private void assertRouteState(String routeId, RouteFsmState expected) {
    assertEquals(expected, router.findRoute(routeId).orElseThrow().fsmState());
  }

  // ── fixture helpers ─────────────────────────────────────────────────────────

  private StagedOrder stageAndReadyOrder(String requestId, long qty) {
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
    StagedOrder order = ((StageResult.Accepted) result).order();
    MarkReadyResult mr = som.markReady(order.orderId(), sessionId);
    return ((MarkReadyResult.Ready) mr).order();
  }

  private String route(StagedOrder order, long qty) {
    RouteResult result =
        router.route(
            new RouteRequest("req-" + order.orderId(), order.orderId(), VENUE, qty, null, null));
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
