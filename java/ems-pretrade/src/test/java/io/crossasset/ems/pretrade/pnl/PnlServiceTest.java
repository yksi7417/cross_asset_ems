/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.pnl;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.pretrade.pnl.PnlService.PnlReport;
import io.crossasset.ems.pretrade.pnl.PnlService.PositionPnl;
import io.crossasset.ems.pretrade.position.PositionService;
import io.crossasset.ems.pretrade.pricing.PricingService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Intraday P&L tests (task 18.7): realized+unrealized off positions and the pricing fallback chain
 * with provenance, FX conversion into the firm base currency, account rollups, and the visible
 * unmarked/unconverted accounting (never silently dropped).
 */
class PnlServiceTest {

  private static final String US_FIGI = "BBG000BLNNH6"; // USD
  private static final String EU_FIGI = "BBG000BB2DM4"; // EUR
  private static final PricingService.FallbackPolicy POLICY =
      PricingService.FallbackPolicy.conservative(60_000L, 600_000L);

  private final PositionService positions = new PositionService();
  private final PricingService pricing = new PricingService();
  private final Map<String, String> currencies =
      Map.of(US_FIGI, "USD", EU_FIGI, "EUR", "BBG000NOCCY0", "JPY");
  private final PnlService pnl =
      new PnlService(positions, pricing, f -> currencies.getOrDefault(f, "USD"), "USD");

  private void fill(String account, String figi, int side, long qty, long px, long eventId) {
    positions.applyFill(
        new PositionService.Fill("E" + eventId, account, figi, side, qty, px, eventId));
  }

  @Test
  void realizedAndUnrealized_markedFromChain_withProvenance() {
    fill("acc-1", US_FIGI, 1, 100, 100_0000L, 1); // buy 100 @ 100
    fill("acc-1", US_FIGI, 2, 40, 110_0000L, 2); // sell 40 @ 110 -> realized 40 * 10 = $400
    pricing.recordLive(US_FIGI, 105_0000L, 9_000L); // mark 105 -> unrealized 60 * 5 = $300

    PnlReport report = pnl.snapshot(List.of("acc-1"), POLICY, 10_000L);

    assertThat(report.rows()).hasSize(1);
    PositionPnl row = report.rows().get(0);
    assertThat(row.netQty()).isEqualTo(60);
    assertThat(row.realizedLocal()).isEqualTo(400_0000L);
    assertThat(row.unrealizedLocal()).isEqualTo(300_0000L);
    assertThat(row.markSource()).startsWith("LIVE:");
    assertThat(report.totalRealizedBase()).isEqualTo(400_0000L);
    assertThat(report.totalUnrealizedBase()).isEqualTo(300_0000L);
    assertThat(report.totalBase()).isEqualTo(700_0000L);
    assertThat(report.unmarked()).isZero();
    assertThat(report.unconverted()).isZero();
  }

  @Test
  void fxConversion_intoBaseCurrency() {
    fill("acc-1", EU_FIGI, 1, 100, 50_0000L, 1); // buy 100 @ EUR 50
    fill("acc-1", EU_FIGI, 2, 100, 52_0000L, 2); // sell 100 @ EUR 52 -> realized EUR 200
    pnl.setFxRate("EUR", 10_852L); // EURUSD 1.0852

    PnlReport report = pnl.snapshot(List.of("acc-1"), POLICY, 0L);

    PositionPnl row = report.rows().get(0);
    assertThat(row.currency()).isEqualTo("EUR");
    assertThat(row.realizedLocal()).isEqualTo(200_0000L);
    assertThat(row.realizedBase()).isEqualTo(200_0000L * 10_852 / 10_000); // $217.04
    assertThat(report.totalRealizedBase()).isEqualTo(2_170_400L);
  }

  @Test
  void unmarkedPosition_reportsRealizedOnly_andIsCounted() {
    fill("acc-1", US_FIGI, 1, 100, 100_0000L, 1);
    fill("acc-1", US_FIGI, 2, 40, 110_0000L, 2);
    // no price recorded -> chain exhausts

    PnlReport report = pnl.snapshot(List.of("acc-1"), POLICY, 10_000L);

    PositionPnl row = report.rows().get(0);
    assertThat(row.markPx()).isNull();
    assertThat(row.unrealizedLocal()).isNull();
    assertThat(row.realizedBase()).isEqualTo(400_0000L);
    assertThat(report.unmarked()).isEqualTo(1);
    assertThat(report.totalBase()).isEqualTo(400_0000L);
  }

  @Test
  void missingFxRate_keepsLocalValues_excludedFromBaseTotals_counted() {
    fill("acc-1", "BBG000NOCCY0", 1, 10, 1_000_0000L, 1);
    fill("acc-1", "BBG000NOCCY0", 2, 10, 1_010_0000L, 2); // realized JPY 100
    fill("acc-1", US_FIGI, 1, 10, 100_0000L, 3);
    fill("acc-1", US_FIGI, 2, 10, 101_0000L, 4); // realized $10

    PnlReport report = pnl.snapshot(List.of("acc-1"), POLICY, 0L);

    PositionPnl jpyRow =
        report.rows().stream().filter(r -> r.currency().equals("JPY")).findFirst().orElseThrow();
    assertThat(jpyRow.realizedLocal()).isEqualTo(100_0000L);
    assertThat(jpyRow.realizedBase()).isNull();
    assertThat(jpyRow.inBaseTotals()).isFalse();
    assertThat(report.unconverted()).isEqualTo(1);
    assertThat(report.totalRealizedBase())
        .as("JPY excluded until a rate exists")
        .isEqualTo(10_0000L);
  }

  @Test
  void multiAccountRollup_sumsAcrossBooks() {
    fill("acc-1", US_FIGI, 1, 10, 100_0000L, 1);
    fill("acc-1", US_FIGI, 2, 10, 105_0000L, 2); // +$50
    fill("acc-2", US_FIGI, 1, 10, 100_0000L, 3);
    fill("acc-2", US_FIGI, 2, 10, 103_0000L, 4); // +$30

    PnlReport report = pnl.snapshot(List.of("acc-1", "acc-2"), POLICY, 0L);

    assertThat(report.rows()).hasSize(2);
    assertThat(report.totalRealizedBase()).isEqualTo(80_0000L);
  }

  @Test
  void staleLive_fallsBackWithVisibleProvenance() {
    fill("acc-1", US_FIGI, 1, 100, 100_0000L, 1);
    pricing.recordLive(US_FIGI, 200_0000L, 0L); // stale vs 60s freshness at t=10m
    pricing.recordPrevClose(US_FIGI, 102_0000L);

    PnlReport report = pnl.snapshot(List.of("acc-1"), POLICY, 600_001L);

    PositionPnl row = report.rows().get(0);
    assertThat(row.markSource()).startsWith("PREV_CLOSE:");
    assertThat(row.unrealizedLocal()).isEqualTo(200_0000L); // 100 * (102 - 100)
  }
}
