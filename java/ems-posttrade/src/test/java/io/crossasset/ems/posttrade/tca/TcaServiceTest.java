/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.tca;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 12.14 + 12.10: hand-computed slippage signs, qty-weighted league ordering, the committee pack,
 * and the ALIGNED / EXCEPTION / DEVIATED adherence verdicts.
 */
class TcaServiceTest {

  private static TcaService.Fill fill(
      String id, int side, long qty, long px, String venue, String broker) {
    return new TcaService.Fill(id, "ORD-" + id, side, qty, px, venue, broker, 1_000L);
  }

  private static final TcaService.BenchmarkSnap BENCH =
      new TcaService.BenchmarkSnap(100_0000, 100_2000, 100_1000, 100_0500, 100_3000L);

  @Test
  void slippageIsCostPositiveForTheSide() {
    TcaService tca = new TcaService();
    // BUY at 100.50 vs arrival 100.00 = paid up 50bp.
    TcaService.FillTca buy = tca.analyze(fill("F1", 1, 100, 100_5000, "XNAS", "GS"), BENCH);
    assertThat(buy.vsArrivalBp()).isEqualTo(50);
    assertThat(buy.vsVwapBp()).isEqualTo(29); // (100.50-100.20)/100.20 → 29.9bp floor
    assertThat(buy.vsPwpBp()).isEqualTo(19);

    // SELL at 100.50 vs arrival 100.00 = sold ABOVE arrival = improvement, negative cost.
    TcaService.FillTca sell = tca.analyze(fill("F2", 2, 100, 100_5000, "XNAS", "GS"), BENCH);
    assertThat(sell.vsArrivalBp()).isEqualTo(-50);
  }

  @Test
  void leagueTables_qtyWeighted_cheapestFirst_deterministicTies() {
    TcaService tca = new TcaService();
    // ARCX: tiny fill at +100bp, huge fill at 0bp → weighted ≈ 0; XNAS: all fills +40bp.
    tca.analyze(fill("A1", 1, 100, 101_0000, "ARCX", "GS"), BENCH); // +100bp
    tca.analyze(fill("A2", 1, 99_900, 100_0000, "ARCX", "GS"), BENCH); // 0bp
    tca.analyze(fill("N1", 1, 1_000, 100_4000, "XNAS", "JPM"), BENCH); // +40bp

    List<TcaService.LeagueRow> venues = tca.venueLeague();
    assertThat(venues.get(0).name()).isEqualTo("ARCX"); // weighted avg ~0bp beats 40bp
    assertThat(venues.get(0).avgArrivalSlippageBp()).isZero();
    assertThat(venues.get(0).worstSlippageBp()).isEqualTo(100); // the outlier still surfaces
    assertThat(venues.get(1).name()).isEqualTo("XNAS");
    assertThat(tca.brokerLeague().get(0).name()).isEqualTo("GS");
  }

  @Test
  void committeePack_carriesLeaguesAndWorstFills_deterministically() {
    TcaService tca = new TcaService();
    tca.analyze(fill("F1", 1, 100, 101_0000, "ARCX", "GS"), BENCH);
    tca.analyze(fill("F2", 1, 100, 100_1000, "XNAS", "JPM"), BENCH);

    String pack = tca.committeePack("2026-06", 1);
    assertThat(pack).contains("period=2026-06").contains("fills=2");
    assertThat(pack).contains("VENUE LEAGUE").contains("BROKER LEAGUE").contains("WORST FILLS");
    assertThat(pack).contains("F1"); // the 100bp outlier is the review item
    assertThat(pack).doesNotContain("F2 order"); // only the requested worst-N
    assertThat(pack).isEqualTo(tca.committeePack("2026-06", 1)); // deterministic bytes
  }

  @Test
  void bestEx_aligned_exception_deviated() {
    BestExAuditor auditor = new BestExAuditor();
    List<BestExAuditor.Alternative> alts =
        List.of(
            new BestExAuditor.Alternative("AXES", "RFQ", 2, "account not eligible"),
            new BestExAuditor.Alternative("JPM", "RFQ", 8, ""));

    // ALIGNED: chose the cheapest available (expected 2bp beats both alternatives' 5/8… here
    // chosen expected = 2 and nothing cheaper exists).
    auditor.recordDecision(
        "ORD-A",
        new BestExAuditor.Decision("D1", "trader-1", "GS", "RFQ", 2, List.of(), "", 1_000L));
    assertThat(auditor.record("ORD-A").orElseThrow().adherence())
        .isEqualTo(BestExAuditor.Adherence.ALIGNED);

    // EXCEPTION: AXES was 3bp better but the rationale documents WHY (ineligible).
    auditor.recordDecision(
        "ORD-B",
        new BestExAuditor.Decision(
            "D2", "trader-1", "GS", "RFQ", 5, alts, "AXES ineligible for account", 2_000L));
    BestExAuditor.BestExRecord exception = auditor.record("ORD-B").orElseThrow();
    assertThat(exception.adherence()).isEqualTo(BestExAuditor.Adherence.EXCEPTION);
    assertThat(exception.explanation()).contains("3bp better").contains("rationale");

    // DEVIATED: a better quote existed and nobody wrote down why it lost.
    auditor.recordDecision(
        "ORD-C", new BestExAuditor.Decision("D3", "trader-2", "GS", "RFQ", 5, alts, "", 3_000L));
    BestExAuditor.BestExRecord deviated = auditor.record("ORD-C").orElseThrow();
    assertThat(deviated.adherence()).isEqualTo(BestExAuditor.Adherence.DEVIATED);
    assertThat(deviated.explanation()).contains("NO RATIONALE");

    // The committee review queue contains exactly the deviation.
    assertThat(auditor.deviations())
        .extracting(BestExAuditor.BestExRecord::orderId)
        .containsExactly("ORD-C");
  }

  @Test
  void bestEx_executionsAttach_andUnknownOrderIsEmpty() {
    BestExAuditor auditor = new BestExAuditor();
    TcaService tca = new TcaService();
    auditor.recordDecision(
        "ORD-X", new BestExAuditor.Decision("D1", "sor-1", "GS", "XNAS", 5, List.of(), "", 1_000L));
    auditor.recordExecution(
        "ORD-X", tca.analyze(fill("F9", 1, 100, 100_5000, "XNAS", "GS"), BENCH));

    BestExAuditor.BestExRecord record = auditor.record("ORD-X").orElseThrow();
    assertThat(record.executions()).hasSize(1);
    assertThat(record.executions().get(0).vsArrivalBp()).isEqualTo(50);
    assertThat(auditor.record("ORD-UNKNOWN")).isEmpty();
  }
}
