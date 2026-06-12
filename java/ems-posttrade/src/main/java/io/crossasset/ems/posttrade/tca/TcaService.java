/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.tca;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transaction Cost Analysis (task 12.14, [[arch-tca]]): per-fill slippage against the 9.5
 * benchmarks, aggregated into venue/broker league tables, exported as the best-ex committee pack.
 * Pure over its inputs — benchmark values arrive as a {@link BenchmarkSnap} (the edge snapshots
 * BenchmarkService; tests pass literals), so the same fills + snapshots replay the identical
 * analysis.
 *
 * <p>Sign convention: slippage is COST-positive for the order's side — a buy executing above the
 * benchmark and a sell executing below it both report positive bps. League tables rank by average
 * arrival slippage ascending (cheapest first); the committee pack carries the tables plus the worst
 * per-fill outliers, which is what a best-ex committee actually reviews.
 */
public final class TcaService {

  /** Benchmark values at a moment (fixed-point 1e4). {@code pwp} may be absent (thin tape). */
  public record BenchmarkSnap(long arrival, long vwap, long twap, long mid, Long pwp) {}

  /** One fill to analyze. */
  public record Fill(
      String fillId,
      String orderId,
      int side,
      long qty,
      long px,
      String venue,
      String broker,
      long atMillis) {}

  /** The per-fill TCA event ([[arch-tca]] § Per-fill TCA event, slippage half). */
  public record FillTca(
      Fill fill, long vsArrivalBp, long vsVwapBp, long vsTwapBp, long vsMidBp, Long vsPwpBp) {}

  /** One league-table row (venue or broker). */
  public record LeagueRow(
      String name, int fills, long totalQty, long avgArrivalSlippageBp, long worstSlippageBp) {}

  private final List<FillTca> analyzed = new ArrayList<>();

  /** Compute one fill's slippage against the snapshots and retain it for aggregation. */
  public FillTca analyze(Fill fill, BenchmarkSnap benchmarks) {
    int sign = fill.side() == 1 ? 1 : -1;
    FillTca tca =
        new FillTca(
            fill,
            slippageBp(fill.px(), benchmarks.arrival(), sign),
            slippageBp(fill.px(), benchmarks.vwap(), sign),
            slippageBp(fill.px(), benchmarks.twap(), sign),
            slippageBp(fill.px(), benchmarks.mid(), sign),
            benchmarks.pwp() == null ? null : slippageBp(fill.px(), benchmarks.pwp(), sign));
    analyzed.add(tca);
    return tca;
  }

  /** Cost-positive slippage in bps: positive = the side paid up vs the benchmark. */
  static long slippageBp(long execPx, long benchmark, int sign) {
    if (benchmark <= 0) {
      return 0;
    }
    return sign * (execPx - benchmark) * 10_000 / benchmark;
  }

  public List<FillTca> fills() {
    return Collections.unmodifiableList(analyzed);
  }

  /** Venue league table, cheapest average arrival slippage first. */
  public List<LeagueRow> venueLeague() {
    return league(tca -> tca.fill().venue());
  }

  /** Broker league table, cheapest first. */
  public List<LeagueRow> brokerLeague() {
    return league(tca -> tca.fill().broker());
  }

  private List<LeagueRow> league(java.util.function.Function<FillTca, String> dimension) {
    Map<String, List<FillTca>> groups = new LinkedHashMap<>();
    for (FillTca tca : analyzed) {
      groups.computeIfAbsent(dimension.apply(tca), k -> new ArrayList<>()).add(tca);
    }
    List<LeagueRow> rows = new ArrayList<>();
    for (Map.Entry<String, List<FillTca>> entry : groups.entrySet()) {
      List<FillTca> fills = entry.getValue();
      // Qty-weighted average: a 1M-share fill matters more than an odd lot.
      long weightedSlippage = 0;
      long totalQty = 0;
      long worst = Long.MIN_VALUE;
      for (FillTca tca : fills) {
        weightedSlippage += tca.vsArrivalBp() * tca.fill().qty();
        totalQty += tca.fill().qty();
        worst = Math.max(worst, tca.vsArrivalBp());
      }
      rows.add(
          new LeagueRow(
              entry.getKey(),
              fills.size(),
              totalQty,
              totalQty > 0 ? weightedSlippage / totalQty : 0,
              worst));
    }
    rows.sort(
        Comparator.comparingLong(LeagueRow::avgArrivalSlippageBp).thenComparing(LeagueRow::name));
    return rows;
  }

  /**
   * The exportable best-ex committee pack: league tables + the {@code outlierCount} worst fills by
   * arrival slippage — deterministic content and ordering for a given fill set.
   */
  public String committeePack(String period, int outlierCount) {
    StringBuilder sb = new StringBuilder();
    sb.append("BEST-EX COMMITTEE PACK | period=").append(period);
    sb.append(" | fills=").append(analyzed.size()).append('\n');
    sb.append("VENUE LEAGUE (qty-weighted arrival slippage, cheapest first)\n");
    for (LeagueRow row : venueLeague()) {
      appendRow(sb, row);
    }
    sb.append("BROKER LEAGUE\n");
    for (LeagueRow row : brokerLeague()) {
      appendRow(sb, row);
    }
    sb.append("WORST FILLS (review items)\n");
    analyzed.stream()
        .sorted(
            Comparator.comparingLong(FillTca::vsArrivalBp)
                .reversed()
                .thenComparing(tca -> tca.fill().fillId()))
        .limit(outlierCount)
        .forEach(
            tca ->
                sb.append("  ")
                    .append(tca.fill().fillId())
                    .append(" order=")
                    .append(tca.fill().orderId())
                    .append(" venue=")
                    .append(tca.fill().venue())
                    .append(" arrivalSlip=")
                    .append(tca.vsArrivalBp())
                    .append("bp vwapSlip=")
                    .append(tca.vsVwapBp())
                    .append("bp\n"));
    return sb.toString();
  }

  private static void appendRow(StringBuilder sb, LeagueRow row) {
    sb.append("  ")
        .append(row.name())
        .append(" fills=")
        .append(row.fills())
        .append(" qty=")
        .append(row.totalQty())
        .append(" avgSlip=")
        .append(row.avgArrivalSlippageBp())
        .append("bp worst=")
        .append(row.worstSlippageBp())
        .append("bp\n");
  }
}
