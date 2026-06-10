/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue;

/**
 * The callback surface a {@link VenueAdapter} invokes to surface venue-side state changes (acks,
 * fills, rejects, cancels, replaces) back toward the router. Methods map 1:1 to the {@code
 * RouteManager} event API; {@link RouteManagerVenueEventSink} bridges them.
 *
 * <p>Keeping adapters coupled only to this interface (not to the OMS directly) preserves the
 * one-way dependency and makes adapters unit-testable with a recording sink.
 */
public interface VenueEventSink {

  /** Venue confirmed the route as working (ExecType=0, OrdStatus=0). */
  void acknowledged(String routeId);

  /** Venue sent Pending New (ExecType=A) before confirming New. */
  void pendingNew(String routeId);

  /** Venue rejected the route on submission (ExecType=8). */
  void rejected(String routeId, String venueReason);

  /** Partial fill (ExecType=F, OrdStatus=1). */
  void partialFill(String routeId, long lastQty, long lastPx, String execId);

  /** Final fill (ExecType=F, OrdStatus=2). */
  void filled(String routeId, long lastQty, long lastPx, String execId);

  /** Venue confirmed cancel (ExecType=4). */
  void canceled(String routeId);

  /** Venue rejected a cancel request (35=9), carrying CxlRejReason (102). */
  void cancelRejected(String routeId, int cxlRejReason);

  /** Venue confirmed a replace (ExecType=5). */
  void replaced(String routeId);

  /** Venue rejected a replace request (35=9), carrying CxlRejReason (102). */
  void replaceRejected(String routeId, int cxlRejReason);
}
