/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.confirmation;

/**
 * Tolerance windows for the confirmation match, reference data per asset class + counterparty (per
 * arch-confirmation-affirmation.md § matching key). The price/qty/accrued tolerances are inclusive
 * absolute deltas; the keyed fields (instrument, side, dates, counterparty) must match exactly.
 */
public record MatchTolerance(long priceTolerance, long qtyTolerance, long accruedTolerance) {

  /** Exact match on all numeric fields (cash equity: price 0 bps). */
  public static MatchTolerance exact() {
    return new MatchTolerance(0, 0, 0);
  }

  /** Corp bond: price within half a tick; qty exact; accrued within a small rounding delta. */
  public static MatchTolerance corpBond(long halfTick, long accruedDelta) {
    return new MatchTolerance(halfTick, 0, accruedDelta);
  }

  /** FX: rate within a configured number of pips; qty exact; no accrued. */
  public static MatchTolerance fx(long pips) {
    return new MatchTolerance(pips, 0, 0);
  }
}
