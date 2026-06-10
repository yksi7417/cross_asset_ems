/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.confirmation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tests for the pure {@link MatchEngine}. */
class MatchEngineTest {

  private static TradeRecord record(long qty, long price, long accrued) {
    return new TradeRecord(
        "TR-1", "US123456AB12", 1, qty, price, accrued, "2026-06-09", "2026-06-11", "CPTY-X");
  }

  @Test
  void identicalRecords_match() {
    MatchResult r =
        MatchEngine.match(record(100, 9950, 12), record(100, 9950, 12), MatchTolerance.exact());
    assertThat(r.matched()).isTrue();
    assertThat(r.differingFields()).isEmpty();
  }

  @Test
  void priceWithinTolerance_matches() {
    // half-tick tolerance of 1; prices differ by 1.
    MatchResult r =
        MatchEngine.match(
            record(100, 9950, 12), record(100, 9951, 12), MatchTolerance.corpBond(1, 0));
    assertThat(r.matched()).isTrue();
  }

  @Test
  void priceOutsideTolerance_unmatchedOnPrice() {
    MatchResult r =
        MatchEngine.match(
            record(100, 9950, 12), record(100, 9955, 12), MatchTolerance.corpBond(1, 0));
    assertThat(r.matched()).isFalse();
    assertThat(r.differingFields()).containsExactly("price");
  }

  @Test
  void qtyMismatch_unmatchedOnQty() {
    MatchResult r =
        MatchEngine.match(record(100, 9950, 12), record(90, 9950, 12), MatchTolerance.exact());
    assertThat(r.differingFields()).containsExactly("qty");
  }

  @Test
  void instrumentAndSideMismatch_bothReported() {
    TradeRecord ours = record(100, 9950, 12);
    TradeRecord theirs =
        new TradeRecord(
            "TR-1", "XX999999ZZ99", 2, 100, 9950, 12, "2026-06-09", "2026-06-11", "CPTY-X");
    MatchResult r = MatchEngine.match(ours, theirs, MatchTolerance.exact());
    assertThat(r.differingFields()).contains("instrumentId", "side");
  }

  @Test
  void accruedWithinTolerance_matches() {
    MatchResult r =
        MatchEngine.match(
            record(100, 9950, 12), record(100, 9950, 14), MatchTolerance.corpBond(0, 2));
    assertThat(r.matched()).isTrue();
  }
}
