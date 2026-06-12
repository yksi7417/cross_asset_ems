/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

/**
 * 10.2: the three fat-finger trips — price deviation, notional ceiling (with netted-vs-unnetted
 * relief for risk-reducing orders), no-reference policy — each a supervisor-overridable BLOCK.
 */
class FatFingerCheckTest {

  private static final String FIGI = "BBG000B9XRY4";
  private static final FatFingerCheck.Policy POLICY =
      new FatFingerCheck.Policy(1_000_000_000_000L, 500, 3, true); // $100k notional, 5% band, 3x

  private static ComplianceOperation stage(int side, long qty, Long price) {
    return new ComplianceOperation(
        ComplianceOperation.Kind.STAGE,
        7L,
        "firm",
        "desk",
        "user",
        null,
        FIGI,
        side,
        qty,
        price,
        "ACC-1");
  }

  @Test
  void typo125InsteadOf12_50_blocksOnPriceDeviation() {
    // The arch note's example: 12.50 typed as 125.0 — a 900% deviation from mid.
    FatFingerCheck check =
        new FatFingerCheck(POLICY, figi -> OptionalLong.of(12_5000), (a, f) -> 0);
    Optional<ComplianceCheck.Finding> finding = check.evaluate(stage(1, 100, 125_0000L));

    assertThat(finding).isPresent();
    assertThat(finding.get().outcome()).isEqualTo(ComplianceOutcome.BLOCK);
    assertThat(finding.get().rationale()).contains("deviates").contains("90000bp");
    assertThat(finding.get().overridePath().requiredTags())
        .contains("#compliance-override-fat-finger");
  }

  @Test
  void extraZeroOnQty_blocksOnNotional_atTheReferencePriceForMarketOrders() {
    // qty 100,000 typed as 1,000,000 on a MKT order: notional 1e12 vs a 5e11 desk ceiling.
    FatFingerCheck check =
        new FatFingerCheck(
            new FatFingerCheck.Policy(500_000_000_000L, 500, 3, true),
            figi -> OptionalLong.of(100_0000),
            (a, f) -> 0);
    assertThat(check.evaluate(stage(1, 1_000_000, null))).isPresent();
    // The intended 100k (1e11) passes the same gate.
    assertThat(check.evaluate(stage(1, 100_000, null))).isEmpty();
  }

  @Test
  void nettedVsUnnetted_riskReducingOrdersGetRelief_riskIncreasingDoNot() {
    // Short 2M shares; buying 1.5M @ 100.00 = 1.5e12 notional — over the 1e12 ceiling raw,
    // but it REDUCES risk: with 3x relief it passes.
    FatFingerCheck check =
        new FatFingerCheck(
            POLICY, figi -> OptionalLong.of(100_0000), (account, figi) -> -2_000_000L);
    assertThat(check.evaluate(stage(1, 1_500_000, null))).isEmpty();

    // The SAME size sold (risk-increasing on a short book) blocks, calling out the relief rule.
    Optional<ComplianceCheck.Finding> blocked = check.evaluate(stage(2, 1_500_000, null));
    assertThat(blocked).isPresent();
    assertThat(blocked.get().rationale()).doesNotContain("netting relief");

    // And even risk-reducing has a ceiling: 4M shares (4e12 > 3e12 relieved) still blocks.
    Optional<ComplianceCheck.Finding> tooBig = check.evaluate(stage(1, 4_000_000, null));
    assertThat(tooBig).isPresent();
    assertThat(tooBig.get().rationale()).contains("netting relief");
  }

  @Test
  void noReferencePrice_marketOrderBlocksPerPolicy_limitOrderStillNotionalChecked() {
    FatFingerCheck strict = new FatFingerCheck(POLICY, figi -> OptionalLong.empty(), (a, f) -> 0);
    Optional<ComplianceCheck.Finding> blocked = strict.evaluate(stage(1, 100, null));
    assertThat(blocked).isPresent();
    assertThat(blocked.get().overridePath().requiredTags()).contains("#compliance-override-no-ref");

    // A LIMIT order without a reference still gets the notional gate off its own limit.
    assertThat(strict.evaluate(stage(1, 100, 100_0000L))).isEmpty();
    assertThat(strict.evaluate(stage(1, 200_000_000, 100_0000L))).isPresent();

    // Lenient policy (off-feed instruments): market order with no reference passes.
    FatFingerCheck lenient =
        new FatFingerCheck(
            new FatFingerCheck.Policy(1_000_000_000_000L, 500, 3, false),
            figi -> OptionalLong.empty(),
            (a, f) -> 0);
    assertThat(lenient.evaluate(stage(1, 100, null))).isEmpty();
  }

  @Test
  void onlyStageAndAmendAreGated() {
    FatFingerCheck check =
        new FatFingerCheck(POLICY, figi -> OptionalLong.of(100_0000), (a, f) -> 0);
    ComplianceOperation route =
        new ComplianceOperation(
            ComplianceOperation.Kind.ROUTE,
            7L,
            "firm",
            "desk",
            "user",
            "ORD-1",
            FIGI,
            1,
            1_000_000_000L,
            null,
            "ACC-1");
    assertThat(check.evaluate(route)).isEmpty();
  }

  @Test
  void contractMultiplier_scalesFuturesNotional() {
    // 500 ES contracts @ 5300.00 with 50x multiplier = 500×5300×50 = 1.325e11 × 1e4 fixed…
    FatFingerCheck check =
        new FatFingerCheck(
            new FatFingerCheck.Policy(1_000_000_000_000L, 500, 1, true),
            figi -> OptionalLong.of(5_300_0000),
            (a, f) -> 0,
            figi -> 50L);
    assertThat(check.evaluate(stage(1, 500, null))).isPresent(); // 1.325e12 > 1e12
    assertThat(check.evaluate(stage(1, 300, null))).isEmpty(); // 7.95e11 ok
  }
}
