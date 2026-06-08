package io.crossasset.ems.fsm;

import io.crossasset.ems.fsm.generated.*;
import org.junit.jupiter.api.Test;

import static io.crossasset.ems.fsm.generated.MultiLegFsmEvent.*;
import static io.crossasset.ems.fsm.generated.MultiLegFsmState.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Correctness tests for compound-guard logic in MultiLegFsmRunner.
 *
 * The MultiLeg guards use integer arithmetic (legs_filled + 1 + legs_rejected + legs_canceled
 * == total_legs) combined with AND/OR. This class verifies the operator precedence and
 * boundary conditions the advisor flagged as unverified after task 1.7.
 *
 * Also includes one smoke transition per FSM not covered by OrderFsmRunnerTest
 * (Route, SOR, VenueSession).
 */
class MultiLegFsmRunnerTest {

    // ── Helpers ─────────────────────────────────────────────────────────────

    static MultiLegFsmContext ctx(String executionMode, long totalLegs,
                                   long legsFilled, long legsRejected, long legsCanceled) {
        return new MultiLegFsmContext(
            "ml-001",
            "PAIRS",
            executionMode,
            totalLegs,
            legsFilled,
            legsRejected,
            legsCanceled,
            null
        );
    }

    static TransitionResult<MultiLegFsmState, MultiLegFsmContext, MultiLegFsmEffect> fire(
        MultiLegFsmState state, MultiLegFsmEvent event, MultiLegFsmContext c, Object payload) {
        return MultiLegFsmRunner.transition(state, event, c, payload);
    }

    static MultiLegFsmPayloads.LegFilledPayload filled() {
        return new MultiLegFsmPayloads.LegFilledPayload("leg-1", 50L, 10000L);
    }

    static MultiLegFsmPayloads.LegRejectedPayload rejected() {
        return new MultiLegFsmPayloads.LegRejectedPayload("leg-1");
    }

    static MultiLegFsmPayloads.LegCanceledPayload canceled() {
        return new MultiLegFsmPayloads.LegCanceledPayload("leg-1");
    }

    // ── LegFilled compound guards ─────────────────────────────────────────────

    @Test
    void legFilled_lastOfTwo_allFilled_transitionsToFilled() {
        // totalLegs=2, legsFilled=1, legsRejected=0, legsCanceled=0 → fire LegFilled
        // Guard: (legsFilled+1)==totalLegs && legsRejected==0 && legsCanceled==0 → FILLED
        var c = ctx("SIMULTANEOUS", 2, 1, 0, 0);
        var result = fire(LEGS_WORKING, LegFilled, c, filled());

        assertFalse(result.isNoTransition());
        assertEquals(FILLED, result.newState());
        assertEquals(2L, result.newContext().legsFilled());
    }

    @Test
    void legFilled_notLastLeg_staysWorking() {
        // totalLegs=3, legsFilled=0 → fire LegFilled
        // Guard: (0+1+0+0) < 3 → stay LEGS_WORKING
        var c = ctx("SIMULTANEOUS", 3, 0, 0, 0);
        var result = fire(LEGS_WORKING, LegFilled, c, filled());

        assertFalse(result.isNoTransition());
        assertEquals(LEGS_WORKING, result.newState());
        assertEquals(1L, result.newContext().legsFilled());
    }

    @Test
    void legFilled_legsIndependent_withPriorReject_transitionsToPartiallyFilled() {
        // totalLegs=2, legsFilled=0, legsRejected=1, executionMode=LEGS_INDEPENDENT
        // → fire LegFilled: (0+1+1+0)==2 && (legsRejected>0) → PARTIALLY_FILLED
        var c = ctx("LEGS_INDEPENDENT", 2, 0, 1, 0);
        var result = fire(LEGS_WORKING, LegFilled, c, filled());

        assertFalse(result.isNoTransition());
        assertEquals(PARTIALLY_FILLED, result.newState(), "Fill + prior reject → partially filled");
        assertEquals(1L, result.newContext().legsFilled());
    }

    @Test
    void legFilled_allFilled_takes_priority_over_independentGuard() {
        // totalLegs=2, legsFilled=1, legsRejected=0, legsCanceled=0, LEGS_INDEPENDENT
        // Guard 1 (FILLED): (1+1)==2 && 0==0 && 0==0 → FILLED
        // Guard 2 (PARTIALLY): LEGS_INDEPENDENT && (1+1+0+0)==2 && (0>0||0>0) → false (no rejects)
        // Expect: FILLED (guard 1 wins)
        var c = ctx("LEGS_INDEPENDENT", 2, 1, 0, 0);
        var result = fire(LEGS_WORKING, LegFilled, c, filled());

        assertFalse(result.isNoTransition());
        assertEquals(FILLED, result.newState(), "No rejects/cancels → FILLED not PARTIALLY_FILLED");
    }

    // ── LegRejected compound guards ───────────────────────────────────────────

    @Test
    void legRejected_legsIndependent_withPriorFill_lastLeg_transitionsToPartiallyFilled() {
        // totalLegs=2, legsFilled=1, legsRejected=0, legsCanceled=0, LEGS_INDEPENDENT
        // → fire LegRejected: legsFilled>0 && (1+0+1+0)==2 → PARTIALLY_FILLED
        var c = ctx("LEGS_INDEPENDENT", 2, 1, 0, 0);
        var result = fire(LEGS_WORKING, LegRejected, c, rejected());

        assertFalse(result.isNoTransition());
        assertEquals(PARTIALLY_FILLED, result.newState());
        assertEquals(1L, result.newContext().legsRejected());
    }

    @Test
    void legRejected_legsIndependent_noFills_allRejected_transitionsToCanceled() {
        // totalLegs=2, legsFilled=0, legsRejected=1, legsCanceled=0, LEGS_INDEPENDENT
        // → fire LegRejected: legsFilled==0 && (0+1+1+0)==2 → CANCELED (treated as full cancel)
        var c = ctx("LEGS_INDEPENDENT", 2, 0, 1, 0);
        var result = fire(LEGS_WORKING, LegRejected, c, rejected());

        assertFalse(result.isNoTransition());
        assertEquals(CANCELED, result.newState(), "All legs rejected with no fills → canceled");
    }

    @Test
    void legRejected_legsIndependent_notLastLeg_staysWorking() {
        // totalLegs=3, legsFilled=1, legsRejected=0, LEGS_INDEPENDENT
        // → fire LegRejected: (1+0+1+0)=2 < 3 → LEGS_WORKING
        var c = ctx("LEGS_INDEPENDENT", 3, 1, 0, 0);
        var result = fire(LEGS_WORKING, LegRejected, c, rejected());

        assertFalse(result.isNoTransition());
        assertEquals(LEGS_WORKING, result.newState());
    }

    @Test
    void legRejected_allOrNone_immediatelyRejects() {
        // ALL_OR_NONE: any rejection → REJECTED immediately
        var c = ctx("ALL_OR_NONE", 2, 0, 0, 0);
        var result = fire(LEGS_WORKING, LegRejected, c, rejected());

        assertFalse(result.isNoTransition());
        assertEquals(REJECTED, result.newState());
    }

    // ── Smoke tests for Route, SOR, VenueSession ──────────────────────────────

    @Test
    void routeFsm_smoke_pending_routeSent_transitionsToSent() {
        var ctx = new RouteFsmContext(
            "route-001", "order-001", "cl-001", null,
            "XNAS", "BBG000BLNNH6", 1, 100L, (Long) null,
            0L, 100L, 0L, "order-001", null
        );
        var result = RouteFsmRunner.transition(RouteFsmState.PENDING, RouteFsmEvent.RouteSent, ctx, null);

        assertFalse(result.isNoTransition(), "RouteFsm: PENDING + RouteSent should transition");
        assertEquals(RouteFsmState.SENT, result.newState());
    }

    @Test
    void sorFsm_smoke_sent_strategyDecided_selfLoop() {
        var ctx = new SorFsmContext(
            "sor-001", "order-001", "cl-001", null,
            "SOR_VIRTUAL", "BBG000BLNNH6", 1, 100L, (Long) null,
            0L, 100L, 0L, "order-001", null, "TWAP"
        );
        var result = SorFsmRunner.transition(
            SorFsmState.SENT, SorFsmEvent.SorStrategyDecided, ctx, null);

        assertFalse(result.isNoTransition(), "SorFsm: SENT + SorStrategyDecided should self-loop");
        assertEquals(SorFsmState.SENT, result.newState());
    }

    @Test
    void venueSessionFsm_smoke_disconnected_connectRequested_transitionsToConnecting() {
        var ctx = new VenueSessionFsmContext(
            "vs-001", 1L, 1L, 30L, false, 0L, 0L, "XNAS"
        );
        var result = VenueSessionFsmRunner.transition(
            VenueSessionFsmState.DISCONNECTED, VenueSessionFsmEvent.ConnectRequested, ctx, null);

        assertFalse(result.isNoTransition(), "VenueSessionFsm: DISCONNECTED + ConnectRequested should transition");
        assertEquals(VenueSessionFsmState.CONNECTING, result.newState());
    }
}
