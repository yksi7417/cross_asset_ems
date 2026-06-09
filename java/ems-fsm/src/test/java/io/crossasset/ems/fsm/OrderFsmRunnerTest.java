package io.crossasset.ems.fsm;

import static io.crossasset.ems.fsm.generated.OrderFsmEvent.*;
import static io.crossasset.ems.fsm.generated.OrderFsmState.*;
import static org.junit.jupiter.api.Assertions.*;

import io.crossasset.ems.fsm.generated.*;
import org.junit.jupiter.api.Test;

/**
 * Key correctness tests for generated OrderFsmRunner.
 *
 * <p>Covers the four categories the advisor called out: (1) Guarded PENDING_REPLACE branches (NEW
 * vs REPLACED restore) (2) Guarded PENDING_CANCEL three-way restore (3) u64 qty arithmetic in
 * PartialFill / FullFill (4) Totality — every (state, event) either transitions or returns
 * noTransition
 */
class OrderFsmRunnerTest {

  // ── Helpers ─────────────────────────────────────────────────────────────

  static OrderFsmContext minimal() {
    return new OrderFsmContext(
        "order-001", // orderId
        "cl-001", // clOrdId
        null, // origClOrdId
        "BBG000BLNNH6", // instrumentId
        1, // side (Buy)
        100L, // orderQty
        (Long) null, // price (nullable i64)
        0L, // cumQty
        100L, // leavesQty
        "ACC-1", // account
        0, // tif (Day)
        "order-001", // initialClOrdId
        "chain-001", // chainId
        0L, // orderVersion
        null, // preCancelStatus
        null // preReplaceStatus
        );
  }

  static TransitionResult<OrderFsmState, OrderFsmContext, OrderFsmEffect> fire(
      OrderFsmState state, OrderFsmEvent event, OrderFsmContext ctx, Object payload) {
    return OrderFsmRunner.transition(state, event, ctx, payload);
  }

  static TransitionResult<OrderFsmState, OrderFsmContext, OrderFsmEffect> fire(
      OrderFsmState state, OrderFsmEvent event, OrderFsmContext ctx) {
    return fire(state, event, ctx, null);
  }

  // ── (1) Guarded PENDING_REPLACE branches ────────────────────────────────

  @Test
  void replaceRejected_fromNew_returnsToNew() {
    // Order was in NEW; entered PENDING_REPLACE; venue rejects → restore to NEW
    OrderFsmContext ctx = minimal();
    // Simulate having entered PENDING_REPLACE from NEW (pre_replace_status saved as "0")
    ctx =
        ctx.with(
            ctx.orderId(),
            ctx.clOrdId(),
            ctx.origClOrdId(),
            ctx.instrumentId(),
            ctx.side(),
            ctx.orderQty(),
            ctx.price(),
            ctx.cumQty(),
            ctx.leavesQty(),
            ctx.account(),
            ctx.tif(),
            ctx.initialClOrdId(),
            ctx.chainId(),
            ctx.orderVersion(),
            ctx.preCancelStatus(),
            "0");

    var payload = new OrderFsmPayloads.ReplaceRejectedPayload(0);
    var result = fire(PENDING_REPLACE, ReplaceRejected, ctx, payload);

    assertFalse(result.isNoTransition(), "Expected a valid transition");
    assertEquals(NEW, result.newState());
  }

  @Test
  void replaceRejected_fromReplaced_returnsToReplaced() {
    // Order was in REPLACED; entered PENDING_REPLACE; venue rejects → restore to REPLACED
    OrderFsmContext ctx = minimal();
    ctx =
        ctx.with(
            ctx.orderId(),
            ctx.clOrdId(),
            ctx.origClOrdId(),
            ctx.instrumentId(),
            ctx.side(),
            ctx.orderQty(),
            ctx.price(),
            ctx.cumQty(),
            ctx.leavesQty(),
            ctx.account(),
            ctx.tif(),
            ctx.initialClOrdId(),
            ctx.chainId(),
            ctx.orderVersion(),
            ctx.preCancelStatus(),
            "5");

    var payload = new OrderFsmPayloads.ReplaceRejectedPayload(0);
    var result = fire(PENDING_REPLACE, ReplaceRejected, ctx, payload);

    assertFalse(result.isNoTransition(), "Expected a valid transition");
    assertEquals(REPLACED, result.newState());
  }

  @Test
  void replaceRejected_unknownPreReplaceStatus_returnsNoTransition() {
    // Guards are exhaustive: unknown status → noTransition
    OrderFsmContext ctx = minimal();
    ctx =
        ctx.with(
            ctx.orderId(),
            ctx.clOrdId(),
            ctx.origClOrdId(),
            ctx.instrumentId(),
            ctx.side(),
            ctx.orderQty(),
            ctx.price(),
            ctx.cumQty(),
            ctx.leavesQty(),
            ctx.account(),
            ctx.tif(),
            ctx.initialClOrdId(),
            ctx.chainId(),
            ctx.orderVersion(),
            ctx.preCancelStatus(),
            "BOGUS");

    var payload = new OrderFsmPayloads.ReplaceRejectedPayload(0);
    var result = fire(PENDING_REPLACE, ReplaceRejected, ctx, payload);

    assertTrue(result.isNoTransition(), "Unknown pre_replace_status must not silently transition");
  }

  // ── (2) Guarded PENDING_CANCEL three-way restore ─────────────────────────

  @Test
  void cancelRejected_preCancelNew_returnsToNew() {
    OrderFsmContext ctx = minimal();
    ctx =
        ctx.with(
            ctx.orderId(),
            ctx.clOrdId(),
            ctx.origClOrdId(),
            ctx.instrumentId(),
            ctx.side(),
            ctx.orderQty(),
            ctx.price(),
            ctx.cumQty(),
            ctx.leavesQty(),
            ctx.account(),
            ctx.tif(),
            ctx.initialClOrdId(),
            ctx.chainId(),
            ctx.orderVersion(),
            "0",
            ctx.preReplaceStatus());

    var payload = new OrderFsmPayloads.CancelRejectedPayload(0);
    var result = fire(PENDING_CANCEL, CancelRejected, ctx, payload);

    assertFalse(result.isNoTransition());
    assertEquals(NEW, result.newState());
  }

  @Test
  void cancelRejected_preCancelReplaced_returnsToReplaced() {
    OrderFsmContext ctx = minimal();
    ctx =
        ctx.with(
            ctx.orderId(),
            ctx.clOrdId(),
            ctx.origClOrdId(),
            ctx.instrumentId(),
            ctx.side(),
            ctx.orderQty(),
            ctx.price(),
            ctx.cumQty(),
            ctx.leavesQty(),
            ctx.account(),
            ctx.tif(),
            ctx.initialClOrdId(),
            ctx.chainId(),
            ctx.orderVersion(),
            "5",
            ctx.preReplaceStatus());

    var payload = new OrderFsmPayloads.CancelRejectedPayload(0);
    var result = fire(PENDING_CANCEL, CancelRejected, ctx, payload);

    assertFalse(result.isNoTransition());
    assertEquals(REPLACED, result.newState());
  }

  @Test
  void cancelRejected_preCancelPartiallyFilled_returnsToPartiallyFilled() {
    OrderFsmContext ctx = minimal();
    ctx =
        ctx.with(
            ctx.orderId(),
            ctx.clOrdId(),
            ctx.origClOrdId(),
            ctx.instrumentId(),
            ctx.side(),
            ctx.orderQty(),
            ctx.price(),
            ctx.cumQty(),
            ctx.leavesQty(),
            ctx.account(),
            ctx.tif(),
            ctx.initialClOrdId(),
            ctx.chainId(),
            ctx.orderVersion(),
            "1",
            ctx.preReplaceStatus());

    var payload = new OrderFsmPayloads.CancelRejectedPayload(0);
    var result = fire(PENDING_CANCEL, CancelRejected, ctx, payload);

    assertFalse(result.isNoTransition());
    assertEquals(PARTIALLY_FILLED, result.newState());
  }

  // ── (3) u64 qty arithmetic ───────────────────────────────────────────────

  @Test
  void partialFill_updatesQtyCorrectly() {
    // orderQty=100, cumQty=0, leavesQty=100; fill 30 → cumQty=30, leavesQty=70
    OrderFsmContext ctx = minimal();
    var payload = new OrderFsmPayloads.PartialFillPayload(30L, 10050L, "EXEC-1");
    var result = fire(NEW, PartialFill, ctx, payload);

    assertFalse(result.isNoTransition());
    assertEquals(PARTIALLY_FILLED, result.newState());
    assertEquals(30L, result.newContext().cumQty(), "cumQty should be 0 + 30");
    assertEquals(70L, result.newContext().leavesQty(), "leavesQty should be 100 - 30");
  }

  @Test
  void fullFill_setsLeavesQtyToZero() {
    // orderQty=100, cumQty=0, leavesQty=100; full fill 100 → cumQty=100, leavesQty=0
    OrderFsmContext ctx = minimal();
    var payload = new OrderFsmPayloads.FullFillPayload(100L, 10050L, "EXEC-2");
    var result = fire(NEW, FullFill, ctx, payload);

    assertFalse(result.isNoTransition());
    assertEquals(FILLED, result.newState());
    assertEquals(100L, result.newContext().cumQty(), "cumQty should be 0 + 100");
    assertEquals(0L, result.newContext().leavesQty(), "leavesQty should be 0 after full fill");
  }

  @Test
  void partialFill_whilePendingReplace_updatesQtyAndLeavesReplace() {
    // FIX D7/D10: fill during pending-replace applies at prior params
    OrderFsmContext ctx = minimal();
    ctx =
        ctx.with(
            ctx.orderId(),
            ctx.clOrdId(),
            ctx.origClOrdId(),
            ctx.instrumentId(),
            ctx.side(),
            ctx.orderQty(),
            ctx.price(),
            ctx.cumQty(),
            ctx.leavesQty(),
            ctx.account(),
            ctx.tif(),
            ctx.initialClOrdId(),
            ctx.chainId(),
            ctx.orderVersion(),
            ctx.preCancelStatus(),
            "0"); // in pending replace

    var payload = new OrderFsmPayloads.PartialFillPayload(5L, 10000L, "EXEC-3");
    var result = fire(PENDING_REPLACE, PartialFill, ctx, payload);

    assertFalse(result.isNoTransition());
    assertEquals(
        PARTIALLY_FILLED,
        result.newState(),
        "Fill during pending-replace must land in PARTIALLY_FILLED");
    assertEquals(5L, result.newContext().cumQty());
    assertEquals(95L, result.newContext().leavesQty());
  }

  // ── (3b) FIX Appendix D race scenarios (task 1.10) ──────────────────────

  @Test
  void fullFill_duringPendingReplace_isTerminalFilled_d5() {
    // FIX D5: full fill wins over in-flight replace — order becomes terminal FILLED.
    OrderFsmContext ctx =
        minimal()
            .with(
                minimal().orderId(),
                minimal().clOrdId(),
                minimal().origClOrdId(),
                minimal().instrumentId(),
                minimal().side(),
                minimal().orderQty(),
                minimal().price(),
                minimal().cumQty(),
                minimal().leavesQty(),
                minimal().account(),
                minimal().tif(),
                minimal().initialClOrdId(),
                minimal().chainId(),
                minimal().orderVersion(),
                minimal().preCancelStatus(),
                "0"); // pre_replace_status = NEW

    var payload = new OrderFsmPayloads.FullFillPayload(100L, 10050L, "EXEC-D5");
    var result = fire(PENDING_REPLACE, FullFill, ctx, payload);

    assertFalse(result.isNoTransition(), "PENDING_REPLACE+FullFill must transition");
    assertEquals(FILLED, result.newState(), "Fill wins: order must be terminal FILLED");
    assertEquals(100L, result.newContext().cumQty(), "cumQty must include the fill");
    assertEquals(0L, result.newContext().leavesQty(), "leavesQty must be 0 after full fill");

    // Verify subsequent ReplaceAccepted from FILLED is noTransition (informational only)
    var lateAck = new OrderFsmPayloads.ReplaceAcceptedPayload("cl-002");
    var lateResult = fire(FILLED, ReplaceAccepted, result.newContext(), lateAck);
    assertTrue(
        lateResult.isNoTransition(),
        "Late ReplaceAccepted on terminal FILLED must be noTransition (informational)");
  }

  @Test
  void fullFill_duringPendingCancel_isTerminalFilled_d4d5() {
    // FIX D4/D5: full fill wins over in-flight cancel — order is terminal FILLED.
    // The eventual 35=9 CancelRejected arrives at FILLED and must be noTransition.
    OrderFsmContext ctx =
        minimal()
            .with(
                minimal().orderId(),
                minimal().clOrdId(),
                minimal().origClOrdId(),
                minimal().instrumentId(),
                minimal().side(),
                minimal().orderQty(),
                minimal().price(),
                minimal().cumQty(),
                minimal().leavesQty(),
                minimal().account(),
                minimal().tif(),
                minimal().initialClOrdId(),
                minimal().chainId(),
                minimal().orderVersion(),
                "0", // pre_cancel_status = NEW
                null);

    var payload = new OrderFsmPayloads.FullFillPayload(100L, 10050L, "EXEC-D4");
    var result = fire(PENDING_CANCEL, FullFill, ctx, payload);

    assertFalse(result.isNoTransition(), "PENDING_CANCEL+FullFill must transition");
    assertEquals(FILLED, result.newState(), "Fill wins: order must be terminal FILLED");
    assertEquals(100L, result.newContext().cumQty());
    assertEquals(0L, result.newContext().leavesQty());

    // The 35=9 CancelRejected that arrives later must be ignored (already terminal)
    var lateCancelReject = new OrderFsmPayloads.CancelRejectedPayload(0);
    var lateResult = fire(FILLED, CancelRejected, result.newContext(), lateCancelReject);
    assertTrue(
        lateResult.isNoTransition(), "Late CancelRejected on terminal FILLED must be noTransition");
  }

  @Test
  void partialFill_duringPendingCancel_updatesQtyAndLeavesActive() {
    // FIX D4: partial fill races against in-flight cancel; fill wins at prior params.
    // Decision: mirrors PENDING_REPLACE+PartialFill pattern (adapter handles eventual ack).
    OrderFsmContext ctx =
        minimal()
            .with(
                minimal().orderId(),
                minimal().clOrdId(),
                minimal().origClOrdId(),
                minimal().instrumentId(),
                minimal().side(),
                minimal().orderQty(),
                minimal().price(),
                minimal().cumQty(),
                minimal().leavesQty(),
                minimal().account(),
                minimal().tif(),
                minimal().initialClOrdId(),
                minimal().chainId(),
                minimal().orderVersion(),
                "0", // pre_cancel_status = NEW
                null);

    var payload = new OrderFsmPayloads.PartialFillPayload(30L, 10050L, "EXEC-D4-PF");
    var result = fire(PENDING_CANCEL, PartialFill, ctx, payload);

    assertFalse(result.isNoTransition(), "PENDING_CANCEL+PartialFill must transition");
    assertEquals(
        PARTIALLY_FILLED,
        result.newState(),
        "Partial fill during cancel lands in PARTIALLY_FILLED");
    assertEquals(30L, result.newContext().cumQty());
    assertEquals(70L, result.newContext().leavesQty());
  }

  // ── (4) Totality ─────────────────────────────────────────────────────────

  @Test
  void totality_allStateEventPairsNeverThrow() {
    // For every (state, event) pair, transition() must return a non-null result or throw
    // ClassCastException (wrong payload type) but never NullPointerException /
    // IllegalStateException — the FSM must be total.
    OrderFsmContext ctx = minimal();
    for (OrderFsmState state : OrderFsmState.values()) {
      for (OrderFsmEvent event : OrderFsmEvent.values()) {
        Object payload = pickPayload(event);
        TransitionResult<?, ?, ?> result;
        try {
          result = OrderFsmRunner.transition(state, event, ctx, payload);
        } catch (ClassCastException e) {
          // Acceptable: wrong payload type for this event in this test
          continue;
        }
        assertNotNull(
            result, "transition() must never return null for state=" + state + " event=" + event);
        if (!result.isNoTransition()) {
          assertNotNull(result.newState(), "non-noTransition must have a newState");
          assertNotNull(result.effects(), "effects list must not be null");
        }
      }
    }
  }

  /** Returns a payload compatible with the event (null for events with no payload schema). */
  private static Object pickPayload(OrderFsmEvent event) {
    return switch (event) {
      case ReplaceRequested ->
          new OrderFsmPayloads.ReplaceRequestedPayload("cl-002", 100L, (Long) null);
      case ReplaceAccepted -> new OrderFsmPayloads.ReplaceAcceptedPayload("cl-002");
      case ReplaceRejected -> new OrderFsmPayloads.ReplaceRejectedPayload(0);
      case CancelRejected -> new OrderFsmPayloads.CancelRejectedPayload(0);
      case PartialFill -> new OrderFsmPayloads.PartialFillPayload(10L, 10000L, "EXEC-T");
      case FullFill -> new OrderFsmPayloads.FullFillPayload(100L, 10000L, "EXEC-T");
      case TradeCorrect -> new OrderFsmPayloads.TradeCorrectPayload(100L, 10000L, "EXEC-T");
      case TradeCancelBust -> new OrderFsmPayloads.TradeCancelBustPayload("EXEC-T");
      default -> null;
    };
  }

  // ── Effect content spot checks ───────────────────────────────────────────

  @Test
  void validationPassed_emitsFixAndEventLog() {
    var result = fire(PENDING_NEW, ValidationPassed, minimal());

    assertFalse(result.isNoTransition());
    assertEquals(NEW, result.newState());

    var effects = result.effects();
    // ChainIdentityStamp was added in the FSM update (stamps initialClOrdId/chainId on acceptance)
    assertEquals(
        3,
        effects.size(),
        "ValidationPassed should emit ChainIdentityStamp + PublishFixMessage + PublishEventLog");
    assertTrue(
        effects.stream().anyMatch(e -> e instanceof OrderFsmEffect.ChainIdentityStamp),
        "Missing ChainIdentityStamp effect");

    var fix =
        effects.stream()
            .filter(e -> e instanceof OrderFsmEffect.PublishFixMessage)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing PublishFixMessage effect"));
    var log =
        effects.stream()
            .filter(e -> e instanceof OrderFsmEffect.PublishEventLog)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing PublishEventLog effect"));

    assertEquals("8", ((OrderFsmEffect.PublishFixMessage) fix).args().get("msg_type"));
    assertEquals("OrderAccepted", ((OrderFsmEffect.PublishEventLog) log).event());
  }

  @Test
  void cancelAccepted_emitsCascadeToRouteFsm() {
    // CancelAccepted from PENDING_CANCEL must emit emit_event to RouteFsm
    OrderFsmContext ctx = minimal();
    ctx =
        ctx.with(
            ctx.orderId(),
            ctx.clOrdId(),
            ctx.origClOrdId(),
            ctx.instrumentId(),
            ctx.side(),
            ctx.orderQty(),
            ctx.price(),
            ctx.cumQty(),
            ctx.leavesQty(),
            ctx.account(),
            ctx.tif(),
            ctx.initialClOrdId(),
            ctx.chainId(),
            ctx.orderVersion(),
            "0",
            ctx.preReplaceStatus());

    var result = fire(PENDING_CANCEL, CancelAccepted, ctx);

    assertFalse(result.isNoTransition());
    assertEquals(CANCELED, result.newState());

    boolean hasCascade =
        result.effects().stream()
            .anyMatch(
                e ->
                    e instanceof OrderFsmEffect.EmitEvent ee
                        && "RouteFsm".equals(ee.targetFsm())
                        && "RouteCancelRequested".equals(ee.event()));
    assertTrue(hasCascade, "CANCELED must cascade RouteCancelRequested to RouteFsm");
  }
}
