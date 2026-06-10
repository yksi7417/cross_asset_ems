/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue;

import org.jspecify.annotations.Nullable;

/**
 * Router-facing submission envelope handed to a {@link VenueAdapter#submit}. Carries the minimum a
 * venue needs to place a child order; the adapter translates it to the venue's wire dialect.
 *
 * @param routeId internal route identifier (correlation key for venue events back to the router)
 * @param clOrdId FIX tag 11 — the ClOrdID sent to the venue
 * @param instrumentId FIGI of the instrument
 * @param side FIX tag 54 (1=buy, 2=sell)
 * @param qty quantity to route
 * @param price limit price in fixed-point, or {@code null} for a market order
 */
public record VenueRouteRequest(
    String routeId, String clOrdId, String instrumentId, int side, long qty, @Nullable Long price) {

  /** True if this is a market order (no limit price). */
  public boolean isMarket() {
    return price == null;
  }
}
