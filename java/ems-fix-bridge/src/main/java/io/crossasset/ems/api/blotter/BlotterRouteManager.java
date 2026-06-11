/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.blotter;

import io.crossasset.ems.oms.Route;
import io.crossasset.ems.oms.RouteEventResult;
import io.crossasset.ems.oms.RouteManager;
import io.crossasset.ems.oms.RouteRequest;
import io.crossasset.ems.oms.RouteResult;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Route-layer blotter decorator (task 18.1): publishes the updated route row after every applied
 * route event, and an append-only fill row on each execution. Order-row propagation needs no code
 * here — the delegate calls the (decorated) staged-order manager to propagate fills, so wiring is
 * {@code new InMemoryRouteManager(blotterSom)} wrapped by this class.
 */
public final class BlotterRouteManager implements RouteManager {

  private final RouteManager delegate;
  private final BlotterPublisher blotter;

  public BlotterRouteManager(RouteManager delegate, BlotterPublisher blotter) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.blotter = Objects.requireNonNull(blotter, "blotter");
  }

  @Override
  public RouteResult route(RouteRequest request) {
    RouteResult result = delegate.route(request);
    if (result instanceof RouteResult.Routed routed) {
      blotter.publishRoute(routed.route());
    }
    return result;
  }

  @Override
  public RouteEventResult acknowledgeRoute(String routeId) {
    return published(delegate.acknowledgeRoute(routeId));
  }

  @Override
  public RouteEventResult pendingNewAtVenue(String routeId) {
    return published(delegate.pendingNewAtVenue(routeId));
  }

  @Override
  public RouteEventResult rejectRoute(String routeId) {
    return published(delegate.rejectRoute(routeId));
  }

  @Override
  public RouteEventResult cancelRoute(String routeId) {
    return published(delegate.cancelRoute(routeId));
  }

  @Override
  public RouteEventResult canceledByVenue(String routeId) {
    return published(delegate.canceledByVenue(routeId));
  }

  @Override
  public RouteEventResult cancelRejectedByVenue(String routeId, int cxlRejReason) {
    return published(delegate.cancelRejectedByVenue(routeId, cxlRejReason));
  }

  @Override
  public RouteEventResult requestReplace(
      String routeId, String newClOrdId, long newQty, @Nullable Long newPrice) {
    return published(delegate.requestReplace(routeId, newClOrdId, newQty, newPrice));
  }

  @Override
  public RouteEventResult replacePendingAtVenue(String routeId) {
    return published(delegate.replacePendingAtVenue(routeId));
  }

  @Override
  public RouteEventResult replacedByVenue(String routeId) {
    return published(delegate.replacedByVenue(routeId));
  }

  @Override
  public RouteEventResult replaceRejectedByVenue(String routeId, int cxlRejReason) {
    return published(delegate.replaceRejectedByVenue(routeId, cxlRejReason));
  }

  @Override
  public RouteEventResult partialFill(String routeId, long lastQty, long lastPx, String execId) {
    RouteEventResult result = delegate.partialFill(routeId, lastQty, lastPx, execId);
    return publishedWithFill(result, execId, lastQty, lastPx);
  }

  @Override
  public RouteEventResult fullFill(String routeId, long lastQty, long lastPx, String execId) {
    RouteEventResult result = delegate.fullFill(routeId, lastQty, lastPx, execId);
    return publishedWithFill(result, execId, lastQty, lastPx);
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
  public List<RouteEventResult> cascadeOrderCancel(String orderId) {
    List<RouteEventResult> results = delegate.cascadeOrderCancel(orderId);
    for (RouteEventResult result : results) {
      published(result);
    }
    return results;
  }

  private RouteEventResult published(RouteEventResult result) {
    if (result instanceof RouteEventResult.Applied applied) {
      blotter.publishRoute(applied.route());
    }
    return result;
  }

  private RouteEventResult publishedWithFill(
      RouteEventResult result, String execId, long lastQty, long lastPx) {
    if (result instanceof RouteEventResult.Applied applied) {
      blotter.publishRoute(applied.route());
      blotter.publishFill(applied.route(), execId, lastQty, lastPx);
    }
    return result;
  }
}
