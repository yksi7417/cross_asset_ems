/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.fees;

/**
 * One broker × asset-class fee schedule (task 12.13). All money is fixed-point 1e4 (the EMS price
 * scale); rates are basis points of gross notional except {@code perUnitFee}, which is money per
 * unit traded (per share / per contract — how US equity commissions and exchange fees actually
 * quote).
 *
 * @param commissionBps broker commission as bps of gross notional
 * @param perUnitFee commission per unit (cents-per-share style), fixed-point 1e4 money
 * @param minCommission commission floor per allocation (fixed-point), 0 = none
 * @param maxCommission commission cap per allocation (fixed-point), 0 = none
 * @param regulatoryFeeBps regulatory levies (SEC §31 / TAF style) as bps of gross, sell-side only
 *     when {@code regFeeSellOnly}
 * @param regFeeSellOnly true for levies charged only on sells (SEC §31)
 */
public record FeeSchedule(
    long commissionBps,
    long perUnitFee,
    long minCommission,
    long maxCommission,
    long regulatoryFeeBps,
    boolean regFeeSellOnly) {

  public FeeSchedule {
    if (commissionBps < 0 || perUnitFee < 0 || regulatoryFeeBps < 0) {
      throw new IllegalArgumentException("fee components must be >= 0");
    }
    if (maxCommission != 0 && maxCommission < minCommission) {
      throw new IllegalArgumentException("maxCommission < minCommission");
    }
  }

  /** A bps-of-notional commission schedule with no per-unit component. */
  public static FeeSchedule bps(long commissionBps) {
    return new FeeSchedule(commissionBps, 0, 0, 0, 0, false);
  }

  /** US-equity-style: cents per share + SEC-style sell-side levy. */
  public static FeeSchedule perShare(long perUnitFee, long regulatoryFeeBps) {
    return new FeeSchedule(0, perUnitFee, 0, 0, regulatoryFeeBps, true);
  }
}
