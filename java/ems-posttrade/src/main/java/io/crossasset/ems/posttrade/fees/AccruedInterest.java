/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.fees;

/**
 * Bond accrued-interest math (task 12.13): {@code accrued = face × coupon × accruedDays/basis}, in
 * fixed-point 1e4 money with banker's-neutral integer rounding (round half up on the final division
 * only — no intermediate truncation, so the result is deterministic and basis-exact).
 *
 * <p>Day-count conventions follow the US conventions for the demo universe's instruments:
 * corporates/munis 30/360, treasuries ACT/ACT (the caller supplies actual days and period basis —
 * calendar resolution is reference-data's job, not money math's).
 */
public final class AccruedInterest {

  private AccruedInterest() {}

  /**
   * @param face face amount, fixed-point 1e4
   * @param couponBps annual coupon in basis points (4.25% = 425)
   * @param accruedDays days of accrual per the instrument's day-count convention
   * @param basisDays the convention's year basis (360 or 365/366 actual)
   */
  public static long accrued(long face, long couponBps, int accruedDays, int basisDays) {
    if (face < 0 || couponBps < 0 || accruedDays < 0 || basisDays <= 0) {
      throw new IllegalArgumentException("invalid accrual inputs");
    }
    // face × coupon/10000 × days/basis, single final rounding.
    java.math.BigInteger numerator =
        java.math.BigInteger.valueOf(face)
            .multiply(java.math.BigInteger.valueOf(couponBps))
            .multiply(java.math.BigInteger.valueOf(accruedDays));
    java.math.BigInteger denominator =
        java.math.BigInteger.valueOf(10_000L).multiply(java.math.BigInteger.valueOf(basisDays));
    java.math.BigInteger[] qr =
        numerator.multiply(java.math.BigInteger.TWO).divideAndRemainder(denominator);
    long doubled = qr[0].longValueExact();
    return (doubled + (doubled >= 0 ? 1 : -1)) / 2; // round half up
  }

  /** 30/360 convention (US corporates/munis/agencies). */
  public static long thirty360(long face, long couponBps, int accruedDays) {
    return accrued(face, couponBps, accruedDays, 360);
  }

  /** ACT/ACT-style with the caller's actual period basis (treasuries). */
  public static long actAct(long face, long couponBps, int accruedDays, int actualBasisDays) {
    return accrued(face, couponBps, accruedDays, actualBasisDays);
  }
}
