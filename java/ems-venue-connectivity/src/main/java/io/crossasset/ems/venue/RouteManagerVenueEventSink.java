/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue;

import io.crossasset.ems.oms.RouteEventResult;
import io.crossasset.ems.oms.RouteManager;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Bridges {@link VenueEventSink} callbacks onto the {@link RouteManager} event API. This is the
 * wire between the venue-connectivity layer and the OMS router: a concrete adapter calls the sink;
 * the sink drives the route FSM.
 *
 * <p>Each call returns a {@link RouteEventResult}; a {@code Rejected} result (e.g. the route is
 * already terminal, or the event is not applicable in the current FSM state) is reported to the
 * optional {@code anomalyHandler} for ops triage rather than thrown — a late or duplicate venue
 * message must not crash the adapter (see arch-fix-appendix-d.md § late ack / RouteAnomaly).
 */
public final class RouteManagerVenueEventSink implements VenueEventSink {

  private static final Logger logger = Logger.getLogger(RouteManagerVenueEventSink.class.getName());

  private final RouteManager routeManager;
  private final Consumer<RouteEventResult.Rejected> anomalyHandler;

  public RouteManagerVenueEventSink(RouteManager routeManager) {
    this(routeManager, rejected -> logger.warning("Venue event rejected: " + rejected));
  }

  public RouteManagerVenueEventSink(
      RouteManager routeManager, Consumer<RouteEventResult.Rejected> anomalyHandler) {
    this.routeManager = Objects.requireNonNull(routeManager, "routeManager");
    this.anomalyHandler = Objects.requireNonNull(anomalyHandler, "anomalyHandler");
  }

  @Override
  public void acknowledged(String routeId) {
    dispatch(routeManager.acknowledgeRoute(routeId));
  }

  @Override
  public void pendingNew(String routeId) {
    dispatch(routeManager.pendingNewAtVenue(routeId));
  }

  @Override
  public void rejected(String routeId, String venueReason) {
    logger.fine(() -> "Route " + routeId + " rejected by venue: " + venueReason);
    dispatch(routeManager.rejectRoute(routeId));
  }

  @Override
  public void partialFill(String routeId, long lastQty, long lastPx, String execId) {
    dispatch(routeManager.partialFill(routeId, lastQty, lastPx, execId));
  }

  @Override
  public void filled(String routeId, long lastQty, long lastPx, String execId) {
    dispatch(routeManager.fullFill(routeId, lastQty, lastPx, execId));
  }

  @Override
  public void canceled(String routeId) {
    dispatch(routeManager.canceledByVenue(routeId));
  }

  @Override
  public void cancelRejected(String routeId, int cxlRejReason) {
    dispatch(routeManager.cancelRejectedByVenue(routeId, cxlRejReason));
  }

  @Override
  public void replaced(String routeId) {
    dispatch(routeManager.replacedByVenue(routeId));
  }

  @Override
  public void replaceRejected(String routeId, int cxlRejReason) {
    dispatch(routeManager.replaceRejectedByVenue(routeId, cxlRejReason));
  }

  private void dispatch(RouteEventResult result) {
    if (result instanceof RouteEventResult.Rejected rejected) {
      anomalyHandler.accept(rejected);
    }
  }
}
