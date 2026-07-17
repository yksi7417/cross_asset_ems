/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.fees;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Commissions / fees / net-money engine (task 12.13): per-broker + per-asset-class schedules,
 * applied at allocation, producing the {@link NetMoney} decomposition a confirm carries — a buyer
 * expects net-money confirms, not clean qty×price.
 *
 * <p>Schedule resolution: exact (broker, assetClass) → broker default (broker, "*") → house default
 * ("*", assetClass) → global ("*", "*") → zero fees. Registration is config-time; lookup is
 * lock-free.
 *
 * <p>Money math is all fixed-point 1e4, integer, round-half-up on each component's final division —
 * deterministic for replay. Bond gross uses the FI convention (clean price per 100 face): {@code
 * gross = face × px / 100}; everything else {@code gross = qty × px × contractMultiplier} (cash
 * equities/FX use multiplier 1; listed futures/options carry a per-contract multiplier).
 */
public final class FeeEngine {

  private record Key(String broker, String assetClass) {}

  private final Map<Key, FeeSchedule> schedules = new ConcurrentHashMap<>();

  public void register(String broker, String assetClass, FeeSchedule schedule) {
    schedules.put(
        new Key(Objects.requireNonNull(broker), Objects.requireNonNull(assetClass)),
        Objects.requireNonNull(schedule));
  }

  /** The schedule that applies, per the resolution chain (zero-fee schedule when none). */
  public FeeSchedule scheduleFor(String broker, String assetClass) {
    FeeSchedule s = schedules.get(new Key(broker, assetClass));
    if (s == null) {
      s = schedules.get(new Key(broker, "*"));
    }
    if (s == null) {
      s = schedules.get(new Key("*", assetClass));
    }
    if (s == null) {
      s = schedules.get(new Key("*", "*"));
    }
    return s != null ? s : new FeeSchedule(0, 0, 0, 0, 0, false);
  }

  /**
   * Compute the money decomposition of one allocation. Cash equities and FX (contract multiplier 1)
   * — delegates to the 8-arg overload for listed futures/options, which carry a per-contract
   * multiplier (e.g. an index future at 50x).
   *
   * @param side FIX side: 1 = buy; everything else treats charges as sale-side
   * @param qty units (shares/contracts) or face for FI
   * @param price fixed-point 1e4; clean-per-100 when {@code fixedIncome}
   * @param accruedInterest pre-computed accrued (see {@link AccruedInterest}), 0 for non-FI
   */
  public NetMoney compute(
      String broker,
      String assetClass,
      int side,
      long qty,
      long price,
      boolean fixedIncome,
      long accruedInterest) {
    return compute(broker, assetClass, side, qty, price, fixedIncome, accruedInterest, 1L);
  }

  /**
   * Compute the money decomposition of one allocation, with an explicit contract multiplier for
   * listed futures/options (e.g. an index future at 50x). Fixed-income has no contract multiplier —
   * bonds always use {@code face × px / 100}; `contractMultiplier` is validated but not applied to gross.
   *
   * @param side FIX side: 1 = buy; everything else treats charges as sale-side
   * @param qty units (shares/contracts) or face for FI
   * @param price fixed-point 1e4; clean-per-100 when {@code fixedIncome}
   * @param accruedInterest pre-computed accrued (see {@link AccruedInterest}), 0 for non-FI
   * @param contractMultiplier units per contract for listed derivatives (>= 1). Not applied when
   *     {@code fixedIncome} is true.
   */
  public NetMoney compute(
      String broker,
      String assetClass,
      int side,
      long qty,
      long price,
      boolean fixedIncome,
      long accruedInterest,
      long contractMultiplier) {
    if (contractMultiplier < 1) {
      throw new IllegalArgumentException("contractMultiplier must be >= 1");
    }
    FeeSchedule schedule = scheduleFor(broker, assetClass);
    // FI gross = face × px/100 (clean price per 100); everything else qty × px × multiplier
    // (listed futures/options carry a per-contract multiplier, e.g. an index future at 50x).
    // Both stay 1e4. multiplyExact throws on overflow rather than silently wrapping.
    long gross =
        fixedIncome
            ? mulDivRound(qty, price, 100)
            : Math.multiplyExact(Math.multiplyExact(qty, price), contractMultiplier);

    long commission =
        mulDivRound(gross, schedule.commissionBps(), 10_000) + qty * schedule.perUnitFee();
    if (schedule.minCommission() > 0 && commission < schedule.minCommission()) {
      commission = schedule.minCommission();
    }
    if (schedule.maxCommission() > 0 && commission > schedule.maxCommission()) {
      commission = schedule.maxCommission();
    }

    boolean isBuy = side == 1;
    long fees =
        schedule.regFeeSellOnly() && isBuy
            ? 0
            : mulDivRound(gross, schedule.regulatoryFeeBps(), 10_000);

    // Buys pay gross + charges + accrued; sells receive gross − charges + accrued.
    long net =
        isBuy
            ? gross + commission + fees + accruedInterest
            : gross - commission - fees + accruedInterest;
    return new NetMoney(gross, commission, fees, accruedInterest, net);
  }

  private static long mulDivRound(long a, long b, long divisor) {
    java.math.BigInteger numerator =
        java.math.BigInteger.valueOf(a).multiply(java.math.BigInteger.valueOf(b));
    java.math.BigInteger doubled =
        numerator.multiply(java.math.BigInteger.TWO).divide(java.math.BigInteger.valueOf(divisor));
    long d = doubled.longValueExact();
    return (d + (d >= 0 ? 1 : -1)) / 2;
  }
}
