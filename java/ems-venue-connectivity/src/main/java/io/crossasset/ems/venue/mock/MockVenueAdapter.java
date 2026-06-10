/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue.mock;

import io.crossasset.ems.venue.AbstractVenueAdapter;
import io.crossasset.ems.venue.Capability;
import io.crossasset.ems.venue.Dialect;
import io.crossasset.ems.venue.VenueEventSink;
import io.crossasset.ems.venue.VenueRef;
import io.crossasset.ems.venue.VenueRouteRequest;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;

/**
 * In-process mock venue adapter (task 11.2 — v0 stands in for MarketAxess). Drives a deterministic
 * venue lifecycle synchronously through the {@link VenueEventSink} so the MVP can route → fill
 * without a real wire.
 *
 * <p>On {@link #submit} the adapter acknowledges the route, then emits fills per the configured
 * {@link FillBehavior}. Fills print at the request's limit price, or {@link #fallbackMarkPx} for
 * market orders. ExecIDs are a deterministic counter so replay is reproducible. In shadow mode all
 * outbound is discarded (no callbacks), matching {@link AbstractVenueAdapter} semantics.
 */
public final class MockVenueAdapter extends AbstractVenueAdapter {

  /** What the venue does after acknowledging a submitted route. */
  public enum FillBehavior {
    /** Acknowledge only; fills are driven externally (e.g. by a test). */
    ACK_ONLY,
    /** Acknowledge, then immediately fully fill the requested quantity. */
    ACK_THEN_FULL_FILL,
    /** Acknowledge, then a partial (half) fill, then fill the remainder. */
    ACK_THEN_PARTIAL_THEN_FULL,
    /** Reject the route on submission. */
    REJECT
  }

  private static final Set<Capability> DEFAULT_CAPS =
      EnumSet.of(
          Capability.SUPPORTS_MARKET,
          Capability.SUPPORTS_LIMIT,
          Capability.SUPPORTS_CANCEL,
          Capability.SUPPORTS_REPLACE);

  private final FillBehavior behavior;
  private final long fallbackMarkPx;
  private final AtomicLong execSeq = new AtomicLong(1);

  public MockVenueAdapter(
      VenueRef venueRef,
      Set<Capability> capabilities,
      VenueEventSink sink,
      boolean shadow,
      FillBehavior behavior,
      long fallbackMarkPx) {
    super(venueRef, capabilities, sink, shadow);
    this.behavior = behavior;
    this.fallbackMarkPx = fallbackMarkPx;
    setState(io.crossasset.ems.venue.VenueState.CONNECTED);
  }

  /** Convenience factory: a connected mock MarketAxess that auto-fully-fills, marks at 100.00. */
  public static MockVenueAdapter marketAxess(VenueEventSink sink) {
    return new MockVenueAdapter(
        new VenueRef("venue-marketaxess-mock", "MAXX", Dialect.FIX),
        DEFAULT_CAPS,
        sink,
        false,
        FillBehavior.ACK_THEN_FULL_FILL,
        10000L);
  }

  @Override
  public void submit(VenueRouteRequest request) {
    if (isShadow()) {
      return;
    }
    String routeId = request.routeId();
    if (behavior == FillBehavior.REJECT) {
      sink().rejected(routeId, "mock venue reject");
      return;
    }

    sink().acknowledged(routeId);
    long px = request.price() != null ? request.price() : fallbackMarkPx;

    switch (behavior) {
      case ACK_ONLY -> {
        /* fills driven externally */
      }
      case ACK_THEN_FULL_FILL -> sink().filled(routeId, request.qty(), px, nextExecId());
      case ACK_THEN_PARTIAL_THEN_FULL -> {
        long half = request.qty() / 2;
        if (half > 0) {
          sink().partialFill(routeId, half, px, nextExecId());
        }
        sink().filled(routeId, request.qty() - half, px, nextExecId());
      }
      case REJECT -> {
        /* handled above */
      }
    }
  }

  @Override
  public void cancel(String routeId) {
    if (!isShadow()) {
      sink().canceled(routeId);
    }
  }

  @Override
  public void replace(String routeId, String newClOrdId, long newQty, @Nullable Long newPrice) {
    if (!isShadow()) {
      sink().replaced(routeId);
    }
  }

  private String nextExecId() {
    return "MOCK-EXEC-" + execSeq.getAndIncrement();
  }
}
