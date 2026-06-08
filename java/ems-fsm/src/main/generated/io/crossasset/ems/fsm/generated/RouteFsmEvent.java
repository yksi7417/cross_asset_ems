// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/route.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py

package io.crossasset.ems.fsm.generated;

/** FSM events for {@link RouteFsmRunner}. */
public enum RouteFsmEvent {
  /** EMS outbound 35=D dispatched to venue adapter. */
  RouteSent,
  /** Venue sent 35=8 ExecType=A (Pending New) before confirming New. */
  RoutePendingNewAtVenue,
  /** Venue confirmed route as working (35=8 ExecType=0 OrdStatus=0). */
  RouteAcknowledged,
  /** Venue rejected route on submission (35=8 ExecType=8). */
  RouteRejected,
  /** EMS dispatched 35=G to venue for this route. */
  RouteReplaceRequested,
  /** Venue acknowledged replace with ExecType=E. */
  RouteReplacePendingAtVenue,
  /** Venue confirmed replace (35=8 ExecType=5). */
  RouteReplaced,
  /** Venue rejected replace via 35=9; route returns to prior working state. */
  RouteReplaceRejected,
  /** EMS dispatched 35=F to venue for this route. */
  RouteCancelRequested,
  /** Venue confirmed cancel (35=8 ExecType=4). */
  RouteCanceled,
  /** Venue rejected cancel via 35=9; route returns to prior state. */
  RouteCancelRejected,
  /** Partial fill from venue (35=8 ExecType=F OrdStatus=1). */
  RoutePartiallyFilled,
  /** Final fill from venue (35=8 ExecType=F OrdStatus=2). */
  RouteFilled,
  /** Venue expired the route at TIF boundary (35=8 ExecType=C). */
  RouteExpired,
  /** Venue does not support in-place replace; prior route closed and new route issued. Emitted by the venue adapter after cancel+resubmit completes.
 */
  RouteSuperseded,
  /** EMS-side state inconsistency detected (e.g. venue reports unknown ClOrdID, or state cannot be reconciled with internal records). Requires ops triage.
 */
  RouteAnomaly;
}
