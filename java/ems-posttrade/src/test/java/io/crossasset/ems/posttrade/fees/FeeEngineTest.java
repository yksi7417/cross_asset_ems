/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * 12.13 money math, pinned with hand-computed values (all fixed-point 1e4): a confirm's net money
 * must be exact, deterministic, and side-correct.
 */
class FeeEngineTest {

  @Test
  void usEquity_centsPerShare_secFeeOnSellsOnly() {
    FeeEngine engine = new FeeEngine();
    // 0.5 cents/share (= 0.005 = 50 in 1e4), SEC-style levy 0.0008% ≈ 0.08bps → use 1bp for a
    // round pin.
    engine.register("GS", "US_EQUITY", FeeSchedule.perShare(50L, 1L));

    // BUY 500 AAPL @ 182.45: gross = 500 × 1,824,500 = 912,250,000 (= $91,225.00)
    NetMoney buy = engine.compute("GS", "US_EQUITY", 1, 500, 1_824_500L, false, 0);
    assertThat(buy.gross()).isEqualTo(912_250_000L);
    assertThat(buy.commission()).isEqualTo(25_000L); // 500 shares × 50 = $2.50
    assertThat(buy.fees()).isZero(); // SEC levy is sell-side only
    assertThat(buy.netMoney()).isEqualTo(912_275_000L); // buyer pays gross + commission

    // SELL the same: fees = 1bp of gross = 91,225; seller receives gross − charges.
    NetMoney sell = engine.compute("GS", "US_EQUITY", 2, 500, 1_824_500L, false, 0);
    assertThat(sell.fees()).isEqualTo(91_225L);
    assertThat(sell.netMoney()).isEqualTo(912_250_000L - 25_000L - 91_225L);
  }

  @Test
  void corporateBond_bpsCommission_accruedGoesToTheSeller() {
    FeeEngine engine = new FeeEngine();
    engine.register("JPM", "US_IG_CORP", FeeSchedule.bps(5)); // 5bp of gross

    // 100k face @ 97.10 clean: gross = 1,000,000,000 × 971,000 / 100... use face fixed-point:
    // face = 100,000 (units) → 100k face in 1e4 = 1,000,000,000; px 97.1 = 971,000.
    // gross = face × px / 100 = 1,000,000,000 × 971,000 / 100 / 1e4... pin with the engine's
    // convention: qty is face units (100_000), price 971_000 → gross = 100000×971000/100
    long face = 100_000L;
    long gross = 100_000L * 971_000L / 100L; // 971,000,000 = $97,100.00 ✓
    // 30/360 accrued: 100k face (1e9 fixed) 3.45% for 60 days = 1e9 × 345/10000 × 60/360
    long accrued = AccruedInterest.thirty360(1_000_000_000L, 345L, 60);
    assertThat(accrued).isEqualTo(5_750_000L); // $575.00

    NetMoney buy = engine.compute("JPM", "US_IG_CORP", 1, face, 971_000L, true, accrued);
    assertThat(buy.gross()).isEqualTo(gross);
    assertThat(buy.commission()).isEqualTo(485_500L); // 5bp of 971,000,000 = 485,500 ($48.55)
    // Buyer pays gross + commission + accrued (the seller earned the coupon to date).
    assertThat(buy.netMoney()).isEqualTo(gross + 485_500L + 5_750_000L);

    NetMoney sell = engine.compute("JPM", "US_IG_CORP", 2, face, 971_000L, true, accrued);
    // Seller receives gross − commission + accrued.
    assertThat(sell.netMoney()).isEqualTo(gross - 485_500L + 5_750_000L);
  }

  @Test
  void treasuryActAct_basisIsTheActualPeriod() {
    // UST 4.25%: 1M face (1e10 fixed), 91 days into a 182-day semiannual period, ACT/ACT
    // basis = 365 actual days: accrued = 1e10 × 425/10000 × 91/365.
    long accrued = AccruedInterest.actAct(10_000_000_000L, 425L, 91, 365);
    assertThat(accrued).isEqualTo(105_958_904L); // = $10,595.8904 round-half-up
  }

  @Test
  void commissionBounds_minFloor_maxCap() {
    FeeEngine engine = new FeeEngine();
    engine.register("UBS", "US_EQUITY", new FeeSchedule(10, 0, 500_000L, 2_000_000L, 0, false));

    // Tiny ticket: 10bp of 10×100.00 = 1,000 → floored to min 500,000 ($50).
    assertThat(engine.compute("UBS", "US_EQUITY", 1, 10, 1_000_000L, false, 0).commission())
        .isEqualTo(500_000L);
    // Huge ticket: 10bp of 1M×100.00 = 10,000,000,000 bp calc → capped at 2,000,000 ($200).
    assertThat(engine.compute("UBS", "US_EQUITY", 1, 1_000_000L, 1_000_000L, false, 0).commission())
        .isEqualTo(2_000_000L);
  }

  @Test
  void scheduleResolution_exactThenBrokerStarThenHouseStarThenZero() {
    FeeEngine engine = new FeeEngine();
    engine.register("GS", "US_EQUITY", FeeSchedule.bps(3));
    engine.register("GS", "*", FeeSchedule.bps(7));
    engine.register("*", "FX", FeeSchedule.bps(1));

    assertThat(engine.scheduleFor("GS", "US_EQUITY").commissionBps()).isEqualTo(3); // exact
    assertThat(engine.scheduleFor("GS", "US_IG_CORP").commissionBps()).isEqualTo(7); // broker *
    assertThat(engine.scheduleFor("MS", "FX").commissionBps()).isEqualTo(1); // house default
    assertThat(engine.scheduleFor("MS", "US_EQUITY").commissionBps()).isZero(); // nothing
  }

  @Test
  void netMoneyCarriesOntoTheConfirm_andMatchesWithinAccruedTolerance() {
    // The 12.13 contract: the engine's accrued lands on the confirm's TradeRecord, and the
    // counterparty's independently-computed accrued (1-unit rounding delta) still MATCHES
    // under the corp-bond tolerance — net-money confirms, not clean qty×price.
    FeeEngine engine = new FeeEngine();
    engine.register("JPM", "US_IG_CORP", FeeSchedule.bps(5));
    long accrued = AccruedInterest.thirty360(1_000_000_000L, 345L, 60);
    NetMoney money = engine.compute("JPM", "US_IG_CORP", 1, 100_000L, 971_000L, true, accrued);

    var ours =
        new io.crossasset.ems.posttrade.confirmation.TradeRecord(
            "TRD-1",
            "BBG00DEMOC29",
            1,
            100_000L,
            971_000L,
            money.accruedInterest(),
            "2026-06-12",
            "2026-06-13",
            "CPTY-X");
    var theirs =
        new io.crossasset.ems.posttrade.confirmation.TradeRecord(
            "TRD-1",
            "BBG00DEMOC29",
            1,
            100_000L,
            971_000L,
            money.accruedInterest() + 1,
            "2026-06-12",
            "2026-06-13",
            "CPTY-X");
    var result =
        io.crossasset.ems.posttrade.confirmation.MatchEngine.match(
            ours,
            theirs,
            io.crossasset.ems.posttrade.confirmation.MatchTolerance.corpBond(2_500L, 100L));
    assertThat(result.matched()).isTrue();
  }

  @Test
  void listedFuture_contractMultiplierScalesGrossAndFees() {
    FeeEngine engine = new FeeEngine();
    // 0.5bp commission, 1bp reg fee (sell-side only) — the point is gross scaling, not the rates.
    engine.register("MS", "LISTED_FUTURE", new FeeSchedule(5, 0, 0, 0, 10, true));

    // BUY 10 index-future contracts @ 4,500.00, multiplier 50: gross = qty × price × multiplier.
    long qty = 10L;
    long price = 45_000_000L; // $4,500.00 in 1e4
    long multiplier = 50L;
    NetMoney buy = engine.compute("MS", "LISTED_FUTURE", 1, qty, price, false, 0, multiplier);
    long expectedGross = Math.multiplyExact(Math.multiplyExact(qty, price), multiplier);
    assertThat(buy.gross()).isEqualTo(expectedGross); // 10 × 45,000,000 × 50 = 22,500,000,000
    // commission scales off the multiplied gross: 5bp of 22,500,000,000 = 11,250,000.
    assertThat(buy.commission()).isEqualTo(11_250_000L);
    assertThat(buy.fees()).isZero(); // reg fee is sell-side only, this is a buy
    assertThat(buy.netMoney()).isEqualTo(expectedGross + 11_250_000L);

    NetMoney sell = engine.compute("MS", "LISTED_FUTURE", 2, qty, price, false, 0, multiplier);
    // sell-side reg fee = 10bp of the same multiplied gross.
    assertThat(sell.fees()).isEqualTo(22_500_000L);
    assertThat(sell.netMoney()).isEqualTo(expectedGross - 11_250_000L - 22_500_000L);
  }

  @Test
  void sevenArgOverload_stillDefaultsToMultiplierOne_cashEquityUnchanged() {
    FeeEngine engine = new FeeEngine();
    engine.register("GS", "US_EQUITY", FeeSchedule.perShare(50L, 1L));

    // Same fixture as usEquity_centsPerShare_secFeeOnSellsOnly: the pre-existing 7-arg call
    // must still yield gross = qty × price (multiplier 1), unaffected by the new overload.
    NetMoney buy = engine.compute("GS", "US_EQUITY", 1, 500, 1_824_500L, false, 0);
    assertThat(buy.gross()).isEqualTo(500L * 1_824_500L);
    assertThat(buy.gross())
        .isEqualTo(engine.compute("GS", "US_EQUITY", 1, 500, 1_824_500L, false, 0, 1L).gross());
  }

  @Test
  void fixedIncome_ignoresContractMultiplier() {
    FeeEngine engine = new FeeEngine();
    engine.register("JPM", "US_IG_CORP", FeeSchedule.bps(5));
    long face = 100_000L;
    long expectedGross = face * 971_000L / 100L; // FI convention: face × px / 100

    // Any contractMultiplier — even a large one a fat-fingered future-style call might pass —
    // must not perturb bond gross; bonds have no contract multiplier.
    NetMoney money = engine.compute("JPM", "US_IG_CORP", 1, face, 971_000L, true, 0, 50L);
    assertThat(money.gross()).isEqualTo(expectedGross);
  }

  @Test
  void contractMultiplier_belowOneRejected() {
    FeeEngine engine = new FeeEngine();
    engine.register("MS", "LISTED_FUTURE", FeeSchedule.bps(5));
    assertThatThrownBy(
            () -> engine.compute("MS", "LISTED_FUTURE", 1, 10, 45_000_000L, false, 0, 0L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void contractMultiplier_overflowThrowsRatherThanWrapping() {
    FeeEngine engine = new FeeEngine();
    engine.register("MS", "LISTED_FUTURE", FeeSchedule.bps(5));
    // qty × price × multiplier overflows long — must throw (ArithmeticException from
    // Math.multiplyExact), not silently wrap like the old `qty * price`.
    assertThatThrownBy(
            () ->
                engine.compute(
                    "MS", "LISTED_FUTURE", 1, Long.MAX_VALUE / 2, 1_000_000L, false, 0, 1_000_000L))
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  void deterministicForReplay_sameInputsSameDecomposition() {
    FeeEngine a = new FeeEngine();
    FeeEngine b = new FeeEngine();
    for (FeeEngine engine : new FeeEngine[] {a, b}) {
      engine.register("GS", "US_EQUITY", FeeSchedule.perShare(50L, 1L));
    }
    assertThat(a.compute("GS", "US_EQUITY", 2, 333, 1_824_567L, false, 0))
        .isEqualTo(b.compute("GS", "US_EQUITY", 2, 333, 1_824_567L, false, 0));
  }
}
