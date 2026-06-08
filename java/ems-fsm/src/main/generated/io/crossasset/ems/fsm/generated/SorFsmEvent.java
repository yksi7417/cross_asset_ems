// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/sor.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py

package io.crossasset.ems.fsm.generated;

/** FSM events for {@link SorFsmRunner}. */
public enum SorFsmEvent {
  /** SOR strategy engine selected a plan (wheel bucket, slicer schedule, etc.) and is dispatching child routes. Self-loop on SENT; parent stays SENT until first child acknowledges. Compliance payload: strategy_id, bucket, alternatives, seed, wheel_def_hash — logged via SorStrategySelected.
 */
  SorStrategyDecided,
  /** Reactive replan triggered by on_child_event(). Strategy adjusted the cascade plan mid-flight (e.g. cancelled a stale child, added a new slice). Self-loop on WORKING.
 */
  SorPlanAdjusted,
  /** EMS outbound 35=D dispatched to SOR virtual venue adapter. */
  RouteSent,
  /** First child returned ExecType=A; parent echoes Pending New. */
  RoutePendingNewAtVenue,
  /** First child confirmed working; parent SOR route is now WORKING. */
  RouteAcknowledged,
  /** Strategy selection or first child submission rejected. */
  RouteRejected,
  /** EMS dispatched 35=G; cascades to all active child routes. */
  RouteReplaceRequested,
  /** Child routes acknowledged replace with ExecType=E. */
  RouteReplacePendingAtVenue,
  /** All child routes confirmed replace. */
  RouteReplaced,
  /** Child route replace rejected; parent returns to WORKING. */
  RouteReplaceRejected,
  /** EMS dispatched 35=F; cascades to all active child routes. */
  RouteCancelRequested,
  /** All child routes confirmed canceled. */
  RouteCanceled,
  /** Child route cancel rejected; parent returns to prior state. */
  RouteCancelRejected,
  /** Child route partial fill aggregated into parent. */
  RoutePartiallyFilled,
  /** All child routes filled; parent route is fully filled. */
  RouteFilled,
  /** Child routes expired at TIF boundary. */
  RouteExpired,
  /** Prior SOR parent closed due to cancel-and-resubmit supersession. */
  RouteSuperseded,
  /** State inconsistency detected; ops triage required. */
  RouteAnomaly;
}
