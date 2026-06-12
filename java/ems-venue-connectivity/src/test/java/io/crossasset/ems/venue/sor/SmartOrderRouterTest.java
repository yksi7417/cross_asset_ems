/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue.sor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 11.11 + 11.12: the sweep never trades through a better price, the wheel's selection modes are
 * replay-stable and bias-auditable, TCA feedback tilts performance-tier weights, and every decision
 * lands in the router's audit chain.
 */
class SmartOrderRouterTest {

  private static final SorStrategy.RouteIntent BUY_3000 =
      new SorStrategy.RouteIntent("RTE-1", "BBG000B9XRY4", 1, 3_000, null, "ACC-1");

  private static final SorStrategy.MarketContext BOOK =
      new SorStrategy.MarketContext(
          List.of(
              new SorStrategy.VenueQuote("XNAS", 99_9000, 800, 100_1000, 1_000),
              new SorStrategy.VenueQuote("ARCX", 99_8500, 500, 100_0500, 1_500), // best ask
              new SorStrategy.VenueQuote("XNYS", 99_9500, 900, 100_2000, 2_000)),
          1_000L);

  @Test
  void sweep_walksPriceTimePriority_neverTradesThrough() {
    SorStrategy.CascadePlan plan = new SweepStrategy().decide(BUY_3000, BOOK);

    // ARCX shows the best ask (100.05): it must be hit FIRST and fully (1500), then XNAS
    // (100.10, 1000), then XNYS (100.20, 500 of its 2000).
    assertThat(plan.children()).hasSize(3);
    assertThat(plan.children().get(0).venue()).isEqualTo("ARCX");
    assertThat(plan.children().get(0).qty()).isEqualTo(1_500);
    assertThat(plan.children().get(1).venue()).isEqualTo("XNAS");
    assertThat(plan.children().get(1).qty()).isEqualTo(1_000);
    assertThat(plan.children().get(2).venue()).isEqualTo("XNYS");
    assertThat(plan.children().get(2).qty()).isEqualTo(500);
    assertThat(plan.rationale()).contains("price-time sweep");
  }

  @Test
  void sweep_limitStopsTheWalk_residualPostsAtBestVenue() {
    // Limit 100.10: XNYS at 100.20 is beyond — its size is unreachable; residual posts.
    SorStrategy.RouteIntent limited =
        new SorStrategy.RouteIntent("RTE-2", "BBG000B9XRY4", 1, 3_000, 100_1000L, "ACC-1");
    SorStrategy.CascadePlan plan = new SweepStrategy().decide(limited, BOOK);

    assertThat(plan.children()).hasSize(3);
    assertThat(plan.children().get(1).venue()).isEqualTo("XNAS");
    SorStrategy.ChildRoute residual = plan.children().get(2);
    assertThat(residual.algo()).isEqualTo("POST");
    assertThat(residual.venue()).isEqualTo("ARCX"); // best price venue hosts the residual
    assertThat(residual.qty()).isEqualTo(500);
    assertThat(residual.px()).isEqualTo(100_1000L);
    assertThat(plan.rationale()).contains("beyond limit");
  }

  @Test
  void wheel_weightedRandom_isReplayStable_andLogsExclusions() {
    List<AlgoWheel.Bucket> buckets =
        List.of(
            new AlgoWheel.Bucket("GS", "VWAP", 25, Map.of("participation", "0.10")),
            new AlgoWheel.Bucket("MS", "IS", 25, Map.of()),
            new AlgoWheel.Bucket("JPM", "TWAP", 50, Map.of()));
    // MS is ineligible for this account (no cpty tag).
    AlgoWheel wheel =
        new AlgoWheel(
            "EQ_WHEEL",
            AlgoWheel.Mode.WEIGHTED_RANDOM,
            buckets,
            (intent, bucket) -> !bucket.broker().equals("MS"),
            broker -> 0L);

    SorStrategy.CascadePlan first = wheel.decide(BUY_3000, BOOK);
    SorStrategy.CascadePlan replay = wheel.decide(BUY_3000, BOOK);
    // Replay-stable: the same route draws the same bucket — no wall-clock entropy.
    assertThat(first.children()).isEqualTo(replay.children());
    assertThat(first.children()).hasSize(1);
    assertThat(first.children().get(0).broker()).isNotEqualTo("MS");
    // The audit rationale names the draw AND what was excluded — unbiased means showing it.
    assertThat(first.rationale()).contains("weighted-random draw").contains("excluded [MS/IS]");

    // A different route may draw differently, but each is itself stable.
    SorStrategy.RouteIntent other =
        new SorStrategy.RouteIntent("RTE-9", "BBG000B9XRY4", 1, 3_000, null, "ACC-1");
    assertThat(wheel.decide(other, BOOK).children())
        .isEqualTo(wheel.decide(other, BOOK).children());
  }

  @Test
  void wheel_roundRobin_rotatesThroughEligibleBuckets() {
    AlgoWheel wheel =
        AlgoWheel.of(
            "RR_WHEEL",
            AlgoWheel.Mode.ROUND_ROBIN,
            List.of(
                new AlgoWheel.Bucket("GS", "VWAP", 1, Map.of()),
                new AlgoWheel.Bucket("JPM", "TWAP", 1, Map.of())));
    assertThat(wheel.decide(BUY_3000, BOOK).children().get(0).broker()).isEqualTo("GS");
    assertThat(wheel.decide(BUY_3000, BOOK).children().get(0).broker()).isEqualTo("JPM");
    assertThat(wheel.decide(BUY_3000, BOOK).children().get(0).broker()).isEqualTo("GS");
  }

  @Test
  void wheel_performanceTier_tcaFeedbackTiltsTowardCheaperBrokers() {
    // Equal base weights; TCA says GS executes at 2bp, JPM at 22bp (20bp worse).
    AlgoWheel wheel =
        new AlgoWheel(
            "PERF_WHEEL",
            AlgoWheel.Mode.PERFORMANCE_TIER,
            List.of(
                new AlgoWheel.Bucket("GS", "VWAP", 10, Map.of()),
                new AlgoWheel.Bucket("JPM", "TWAP", 10, Map.of())),
            (intent, bucket) -> true,
            broker -> broker.equals("GS") ? 2L : 22L);

    // GS weight: 10×(1+20)=210, JPM: 10×(1+0)=10 → GS wins ~95% of draws. Sample many routes.
    int gsWins = 0;
    for (int i = 0; i < 200; i++) {
      SorStrategy.RouteIntent intent =
          new SorStrategy.RouteIntent("RTE-" + i, "BBG000B9XRY4", 1, 100, null, "ACC-1");
      if (wheel.decide(intent, BOOK).children().get(0).broker().equals("GS")) {
        gsWins++;
      }
    }
    assertThat(gsWins).isGreaterThan(170); // ≈95% expected; the tilt must be decisive
    assertThat(gsWins).isLessThan(200); // but not absolute — JPM still gets flow to stay measured
  }

  @Test
  void router_logsEverySelection_forComplianceReconstruction() {
    SmartOrderRouter router = new SmartOrderRouter();
    router.register(new SweepStrategy());
    router.route(BUY_3000, "sweep", BOOK);

    assertThat(router.selections()).hasSize(1);
    SmartOrderRouter.SelectionEvent event = router.selections().get(0);
    assertThat(event.routeId()).isEqualTo("RTE-1");
    assertThat(event.strategyId()).isEqualTo("sweep");
    assertThat(event.strategyVersion()).isEqualTo(1);
    assertThat(event.plan().rationale()).isNotBlank();
  }
}
