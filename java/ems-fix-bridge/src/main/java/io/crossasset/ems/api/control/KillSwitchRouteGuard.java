/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.control;

import io.crossasset.ems.aaa.AaaService;
import io.crossasset.ems.aaa.Session;
import io.crossasset.ems.oms.Route;
import io.crossasset.ems.oms.RouteEventResult;
import io.crossasset.ems.oms.RouteManager;
import io.crossasset.ems.oms.RouteRequest;
import io.crossasset.ems.oms.RouteResult;
import io.crossasset.ems.oms.StagedOrder;
import io.crossasset.ems.oms.StagedOrderManager;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Routing kill-switch guard (task 18.4): outermost {@link RouteManager} decorator. New routes are
 * locked out when a FIRM/DESK scope covers the order's owning session, or a VENUE scope covers the
 * target MIC. Everything else — cancels, replaces, venue confirmations, fills — passes through:
 * winding down exposure must keep working while the switch is engaged.
 *
 * <p>Fail-secure like the order guard: if the order or its session cannot be resolved while any
 * scope is engaged, the route is locked out.
 */
public final class KillSwitchRouteGuard implements RouteManager {

  private final RouteManager delegate;
  private final KillSwitchState state;
  private final AaaService aaa;
  private final StagedOrderManager som;

  public KillSwitchRouteGuard(
      RouteManager delegate, KillSwitchState state, AaaService aaa, StagedOrderManager som) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.state = Objects.requireNonNull(state, "state");
    this.aaa = Objects.requireNonNull(aaa, "aaa");
    this.som = Objects.requireNonNull(som, "som");
  }

  @Override
  public RouteResult route(RouteRequest request) {
    if (routingLocked(request.orderId(), request.venueMic())) {
      return new RouteResult.Rejected(
          request.requestId(),
          "EMS-RTE-9601",
          "Kill switch engaged — routing is locked out. Route cancels remain allowed.");
    }
    return delegate.route(request);
  }

  private boolean routingLocked(String orderId, String venueMic) {
    if (state.engagedScopes().isEmpty()) {
      return false;
    }
    Optional<StagedOrder> order = som.findOrder(orderId);
    if (order.isEmpty()) {
      return true;
    }
    Optional<Session> session = aaa.sessionInfo(order.get().sessionId());
    if (session.isEmpty()) {
      return true;
    }
    return state.routingLocked(
        session.get().identity().firmId(), session.get().identity().deskId(), venueMic);
  }

  // ── Pass-through: cancels, venue events, fills ───────────────────────────────

  @Override
  public RouteEventResult acknowledgeRoute(String routeId) {
    return delegate.acknowledgeRoute(routeId);
  }

  @Override
  public RouteEventResult pendingNewAtVenue(String routeId) {
    return delegate.pendingNewAtVenue(routeId);
  }

  @Override
  public RouteEventResult rejectRoute(String routeId) {
    return delegate.rejectRoute(routeId);
  }

  @Override
  public RouteEventResult cancelRoute(String routeId) {
    return delegate.cancelRoute(routeId);
  }

  @Override
  public RouteEventResult canceledByVenue(String routeId) {
    return delegate.canceledByVenue(routeId);
  }

  @Override
  public RouteEventResult cancelRejectedByVenue(String routeId, int cxlRejReason) {
    return delegate.cancelRejectedByVenue(routeId, cxlRejReason);
  }

  @Override
  public RouteEventResult requestReplace(
      String routeId, String newClOrdId, long newQty, @Nullable Long newPrice) {
    return delegate.requestReplace(routeId, newClOrdId, newQty, newPrice);
  }

  @Override
  public RouteEventResult replacePendingAtVenue(String routeId) {
    return delegate.replacePendingAtVenue(routeId);
  }

  @Override
  public RouteEventResult replacedByVenue(String routeId) {
    return delegate.replacedByVenue(routeId);
  }

  @Override
  public RouteEventResult replaceRejectedByVenue(String routeId, int cxlRejReason) {
    return delegate.replaceRejectedByVenue(routeId, cxlRejReason);
  }

  @Override
  public RouteEventResult partialFill(String routeId, long lastQty, long lastPx, String execId) {
    return delegate.partialFill(routeId, lastQty, lastPx, execId);
  }

  @Override
  public RouteEventResult fullFill(String routeId, long lastQty, long lastPx, String execId) {
    return delegate.fullFill(routeId, lastQty, lastPx, execId);
  }

  @Override
  public Optional<Route> findRoute(String routeId) {
    return delegate.findRoute(routeId);
  }

  @Override
  public List<Route> findRoutesForOrder(String orderId) {
    return delegate.findRoutesForOrder(orderId);
  }

  @Override
  public List<Route> activeRoutes() {
    return delegate.activeRoutes();
  }

  @Override
  public List<RouteEventResult> cascadeOrderCancel(String orderId) {
    return delegate.cascadeOrderCancel(orderId);
  }
}
