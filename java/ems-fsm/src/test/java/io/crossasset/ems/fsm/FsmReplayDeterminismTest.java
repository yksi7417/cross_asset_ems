package io.crossasset.ems.fsm;

import static io.crossasset.ems.fsm.generated.OrderFsmEvent.*;
import static io.crossasset.ems.fsm.generated.OrderFsmState.*;
import static org.junit.jupiter.api.Assertions.*;

import io.crossasset.ems.fsm.generated.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Replay determinism harness for OrderFsmRunner.
 *
 * <p>Records a multi-step scenario (forward pass) then re-runs each step from scratch (replay pass)
 * and asserts that transition() produces identical outputs every time. This verifies that the
 * generated FSM is a pure function with no hidden state.
 */
class FsmReplayDeterminismTest {

  // ── Replay infrastructure ────────────────────────────────────────────────

  /** One recorded step in the scenario log. */
  private record ReplayEntry(
      OrderFsmState inputState,
      OrderFsmEvent event,
      OrderFsmContext inputCtx,
      Object payload,
      // Captured expected outputs
      OrderFsmState expectedNewState,
      boolean expectedNoTransition,
      // All context fields captured for determinism check
      String expectedOrderId,
      String expectedClOrdId,
      String expectedOrigClOrdId,
      String expectedInstrumentId,
      int expectedSide,
      long expectedOrderQty,
      Long expectedPrice,
      long expectedCumQty,
      long expectedLeavesQty,
      String expectedAccount,
      int expectedTif,
      long expectedTraceId,
      String expectedInitialOrderId,
      String expectedPreCancelStatus,
      String expectedPreReplaceStatus) {}

  private static ReplayEntry capture(
      OrderFsmState state,
      OrderFsmEvent event,
      OrderFsmContext ctx,
      Object payload,
      TransitionResult<OrderFsmState, OrderFsmContext, OrderFsmEffect> result) {
    OrderFsmContext nc = result.newContext();
    return new ReplayEntry(
        state,
        event,
        ctx,
        payload,
        result.newState(),
        result.isNoTransition(),
        nc.orderId(),
        nc.clOrdId(),
        nc.origClOrdId(),
        nc.instrumentId(),
        nc.side(),
        nc.orderQty(),
        nc.price(),
        nc.cumQty(),
        nc.leavesQty(),
        nc.account(),
        nc.tif(),
        nc.traceId(),
        nc.initialOrderId(),
        nc.preCancelStatus(),
        nc.preReplaceStatus());
  }

  private static void replayAndAssert(List<ReplayEntry> log) {
    for (ReplayEntry e : log) {
      var result = OrderFsmRunner.transition(e.inputState(), e.event(), e.inputCtx(), e.payload());
      String tag = e.inputState() + "+" + e.event();
      assertEquals(e.expectedNewState(), result.newState(), tag + ": newState");
      assertEquals(e.expectedNoTransition(), result.isNoTransition(), tag + ": isNoTransition");
      if (!e.expectedNoTransition()) {
        OrderFsmContext nc = result.newContext();
        assertEquals(e.expectedOrderId(), nc.orderId(), tag + ": orderId");
        assertEquals(e.expectedClOrdId(), nc.clOrdId(), tag + ": clOrdId");
        assertEquals(e.expectedOrigClOrdId(), nc.origClOrdId(), tag + ": origClOrdId");
        assertEquals(e.expectedInstrumentId(), nc.instrumentId(), tag + ": instrumentId");
        assertEquals(e.expectedSide(), nc.side(), tag + ": side");
        assertEquals(e.expectedOrderQty(), nc.orderQty(), tag + ": orderQty");
        assertEquals(e.expectedPrice(), nc.price(), tag + ": price");
        assertEquals(e.expectedCumQty(), nc.cumQty(), tag + ": cumQty");
        assertEquals(e.expectedLeavesQty(), nc.leavesQty(), tag + ": leavesQty");
        assertEquals(e.expectedAccount(), nc.account(), tag + ": account");
        assertEquals(e.expectedTif(), nc.tif(), tag + ": tif");
        assertEquals(e.expectedTraceId(), nc.traceId(), tag + ": traceId");
        assertEquals(e.expectedInitialOrderId(), nc.initialOrderId(), tag + ": initialOrderId");
        assertEquals(e.expectedPreCancelStatus(), nc.preCancelStatus(), tag + ": preCancelStatus");
        assertEquals(
            e.expectedPreReplaceStatus(), nc.preReplaceStatus(), tag + ": preReplaceStatus");
      }
    }
  }

  // ── Helpers ─────────────────────────────────────────────────────────────

  static OrderFsmContext minimal() {
    return new OrderFsmContext(
        "order-001",
        "cl-001",
        null,
        "BBG000BLNNH6",
        1,
        100L,
        null,
        0L,
        100L,
        "ACC-1",
        0,
        0L,
        "order-001",
        null,
        null);
  }

  static TransitionResult<OrderFsmState, OrderFsmContext, OrderFsmEffect> fire(
      OrderFsmState state, OrderFsmEvent event, OrderFsmContext ctx, Object payload) {
    return OrderFsmRunner.transition(state, event, ctx, payload);
  }

  static TransitionResult<OrderFsmState, OrderFsmContext, OrderFsmEffect> fire(
      OrderFsmState state, OrderFsmEvent event, OrderFsmContext ctx) {
    return fire(state, event, ctx, null);
  }

  // ── Scenario 1: Fill path ────────────────────────────────────────────────

  @Test
  void fillPath_isReplayDeterministic() {
    List<ReplayEntry> log = new ArrayList<>();
    OrderFsmState state = PENDING_NEW;
    OrderFsmContext ctx = minimal();

    // PENDING_NEW + ValidationPassed → NEW
    var r1 = fire(state, ValidationPassed, ctx);
    assertFalse(r1.isNoTransition());
    assertEquals(NEW, r1.newState());
    log.add(capture(state, ValidationPassed, ctx, null, r1));
    state = r1.newState();
    ctx = r1.newContext();

    // NEW + PartialFill(40 @ 100) → PARTIALLY_FILLED, cumQty=40, leavesQty=60
    var pf1 = new OrderFsmPayloads.PartialFillPayload(40L, 10000L, "E-1");
    var r2 = fire(state, PartialFill, ctx, pf1);
    assertFalse(r2.isNoTransition());
    assertEquals(PARTIALLY_FILLED, r2.newState());
    assertEquals(40L, r2.newContext().cumQty());
    assertEquals(60L, r2.newContext().leavesQty());
    log.add(capture(state, PartialFill, ctx, pf1, r2));
    state = r2.newState();
    ctx = r2.newContext();

    // PARTIALLY_FILLED + PartialFill(30 @ 100) → PARTIALLY_FILLED, cumQty=70, leavesQty=30
    var pf2 = new OrderFsmPayloads.PartialFillPayload(30L, 10000L, "E-2");
    var r3 = fire(state, PartialFill, ctx, pf2);
    assertFalse(r3.isNoTransition());
    assertEquals(PARTIALLY_FILLED, r3.newState());
    assertEquals(70L, r3.newContext().cumQty());
    assertEquals(30L, r3.newContext().leavesQty());
    log.add(capture(state, PartialFill, ctx, pf2, r3));
    state = r3.newState();
    ctx = r3.newContext();

    // PARTIALLY_FILLED + FullFill(30 @ 100) → FILLED, cumQty=100, leavesQty=0
    var ff = new OrderFsmPayloads.FullFillPayload(30L, 10000L, "E-3");
    var r4 = fire(state, FullFill, ctx, ff);
    assertFalse(r4.isNoTransition());
    assertEquals(FILLED, r4.newState());
    assertEquals(100L, r4.newContext().cumQty());
    assertEquals(0L, r4.newContext().leavesQty());
    log.add(capture(state, FullFill, ctx, ff, r4));

    replayAndAssert(log);
  }

  // ── Scenario 2: Replace path ─────────────────────────────────────────────

  @Test
  void replacePath_isReplayDeterministic() {
    List<ReplayEntry> log = new ArrayList<>();
    OrderFsmState state = PENDING_NEW;
    OrderFsmContext ctx = minimal();

    // PENDING_NEW → NEW
    var r1 = fire(state, ValidationPassed, ctx);
    log.add(capture(state, ValidationPassed, ctx, null, r1));
    state = r1.newState();
    ctx = r1.newContext();

    // NEW + ReplaceRequested → PENDING_REPLACE, preReplaceStatus="0"
    var rreq = new OrderFsmPayloads.ReplaceRequestedPayload("cl-002", 200L, null);
    var r2 = fire(state, ReplaceRequested, ctx, rreq);
    assertFalse(r2.isNoTransition());
    assertEquals(PENDING_REPLACE, r2.newState());
    assertEquals("0", r2.newContext().preReplaceStatus());
    log.add(capture(state, ReplaceRequested, ctx, rreq, r2));
    state = r2.newState();
    ctx = r2.newContext();

    // PENDING_REPLACE + ReplaceAccepted → REPLACED, preReplaceStatus=null
    var racc = new OrderFsmPayloads.ReplaceAcceptedPayload("cl-002");
    var r3 = fire(state, ReplaceAccepted, ctx, racc);
    assertFalse(r3.isNoTransition());
    assertEquals(REPLACED, r3.newState());
    assertNull(r3.newContext().preReplaceStatus());
    log.add(capture(state, ReplaceAccepted, ctx, racc, r3));

    replayAndAssert(log);
  }

  // ── Scenario 3: Cancel path ──────────────────────────────────────────────

  @Test
  void cancelPath_isReplayDeterministic() {
    List<ReplayEntry> log = new ArrayList<>();
    OrderFsmState state = PENDING_NEW;
    OrderFsmContext ctx = minimal();

    // PENDING_NEW → NEW
    var r1 = fire(state, ValidationPassed, ctx);
    log.add(capture(state, ValidationPassed, ctx, null, r1));
    state = r1.newState();
    ctx = r1.newContext();

    // NEW + CancelRequested → PENDING_CANCEL, preCancelStatus="0"
    var r2 = fire(state, CancelRequested, ctx);
    assertFalse(r2.isNoTransition());
    assertEquals(PENDING_CANCEL, r2.newState());
    assertEquals("0", r2.newContext().preCancelStatus());
    log.add(capture(state, CancelRequested, ctx, null, r2));
    state = r2.newState();
    ctx = r2.newContext();

    // PENDING_CANCEL + CancelAccepted → CANCELED
    var r3 = fire(state, CancelAccepted, ctx);
    assertFalse(r3.isNoTransition());
    assertEquals(CANCELED, r3.newState());
    log.add(capture(state, CancelAccepted, ctx, null, r3));

    replayAndAssert(log);
  }

  // ── Scenario 4: Partial-fill then cancel ────────────────────────────────

  @Test
  void partialFillThenCancel_isReplayDeterministic() {
    List<ReplayEntry> log = new ArrayList<>();
    OrderFsmState state = PENDING_NEW;
    OrderFsmContext ctx = minimal();

    // PENDING_NEW → NEW
    var r1 = fire(state, ValidationPassed, ctx);
    log.add(capture(state, ValidationPassed, ctx, null, r1));
    state = r1.newState();
    ctx = r1.newContext();

    // NEW → PARTIALLY_FILLED
    var pf = new OrderFsmPayloads.PartialFillPayload(50L, 10000L, "E-1");
    var r2 = fire(state, PartialFill, ctx, pf);
    log.add(capture(state, PartialFill, ctx, pf, r2));
    state = r2.newState();
    ctx = r2.newContext();
    assertEquals(PARTIALLY_FILLED, state);

    // PARTIALLY_FILLED + CancelRequested → PENDING_CANCEL, preCancelStatus="1"
    var r3 = fire(state, CancelRequested, ctx);
    assertFalse(r3.isNoTransition());
    assertEquals(PENDING_CANCEL, r3.newState());
    assertEquals("1", r3.newContext().preCancelStatus());
    log.add(capture(state, CancelRequested, ctx, null, r3));

    replayAndAssert(log);
  }
}
