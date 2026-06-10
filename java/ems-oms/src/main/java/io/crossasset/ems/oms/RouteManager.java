/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Manages the route lifecycle on behalf of the EMS.
 *
 * <p>Routes are the EMS's obligations to venues. Each route tracks its own FIX-aligned FSM (PENDING
 * → SENT → WORKING → fills/cancel/replace/terminal) via {@link
 * io.crossasset.ems.fsm.generated.RouteFsmRunner}.
 *
 * <p>Fill events propagate back to the parent order's FSM (PartialFill / FullFill / OrderExpired)
 * via the injected {@link StagedOrderManager}.
 *
 * <p>Out of scope for this layer: venue wire encoding (task 11.x), SOR split decisions, algo
 * selection, quote distribution.
 *
 * <p>Per arch-router-layer.md, task 7.2.
 */
public interface RouteManager {

  /**
   * Creates a route from a READY or ROUTING staged order. Fires RouteSent immediately, advancing
   * the new route from PENDING to SENT.
   *
   * <p>Rejects with EMS-RTE-4001 if orderId unknown, EMS-RTE-4002 if order not READY/ROUTING,
   * EMS-RTE-4003 if qty exceeds order remaining, EMS-RTE-2005 on ClOrdID collision.
   */
  RouteResult route(RouteRequest request);

  /**
   * Venue acknowledged the route (SENT or PENDING_NEW_AT_VENUE → WORKING). Propagates
   * ValidationPassed to the order FSM (harmless no-transition if order is already NEW).
   */
  RouteEventResult acknowledgeRoute(String routeId);

  /**
   * Venue sent ExecType=A before confirming New (SENT → PENDING_NEW_AT_VENUE). No order FSM effect.
   */
  RouteEventResult pendingNewAtVenue(String routeId);

  /** Venue rejected the route on submission (SENT → REJECTED). */
  RouteEventResult rejectRoute(String routeId);

  /**
   * Dispatches a cancel to the venue (WORKING or PARTIALLY_FILLED → PENDING_CANCEL_AT_VENUE). Sets
   * preCancelStatus so the FSM can restore the correct prior state on cancel rejection.
   */
  RouteEventResult cancelRoute(String routeId);

  /** Venue confirmed cancel (PENDING_CANCEL_AT_VENUE → CANCELED). */
  RouteEventResult canceledByVenue(String routeId);

  /**
   * Venue rejected cancel (PENDING_CANCEL_AT_VENUE → WORKING or PARTIALLY_FILLED, per
   * preCancelStatus). Propagates CancelRejected to the order FSM with the given reason code.
   */
  RouteEventResult cancelRejectedByVenue(String routeId, int cxlRejReason);

  /**
   * Dispatches a replace to the venue (WORKING → PENDING_REPLACE_AT_VENUE). Note: the route context
   * qty/price are NOT updated until the venue confirms with {@link #replacedByVenue}.
   */
  RouteEventResult requestReplace(
      String routeId, String newClOrdId, long newQty, @Nullable Long newPrice);

  /** Venue acknowledged replace (still in PENDING_REPLACE_AT_VENUE). */
  RouteEventResult replacePendingAtVenue(String routeId);

  /**
   * Venue confirmed replace (PENDING_REPLACE_AT_VENUE → WORKING). Propagates ReplaceAccepted to the
   * order FSM (no-transition if order is in NEW state; meaningful only for order-level replaces).
   */
  RouteEventResult replacedByVenue(String routeId);

  /**
   * Venue rejected replace (PENDING_REPLACE_AT_VENUE → WORKING). Propagates ReplaceRejected to the
   * order FSM.
   */
  RouteEventResult replaceRejectedByVenue(String routeId, int cxlRejReason);

  /**
   * Records a partial fill (WORKING or PARTIALLY_FILLED → PARTIALLY_FILLED). Updates
   * cumQty/leavesQty on the route and propagates PartialFill to the parent order.
   */
  RouteEventResult partialFill(String routeId, long lastQty, long lastPx, String execId);

  /**
   * Records the final fill (WORKING or PARTIALLY_FILLED → FILLED). Updates cumQty/leavesQty on the
   * route and propagates FullFill to the parent order.
   */
  RouteEventResult fullFill(String routeId, long lastQty, long lastPx, String execId);

  /** Returns the route if it exists, empty otherwise. */
  Optional<Route> findRoute(String routeId);

  /** Returns all routes for the given orderId (including terminal routes). */
  List<Route> findRoutesForOrder(String orderId);
}
