/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.surveillance;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.posttrade.surveillance.SurveillanceEvent.Type;
import io.crossasset.ems.posttrade.surveillance.detectors.FatFingerClusterDetector;
import io.crossasset.ems.posttrade.surveillance.detectors.LayeringDetector;
import io.crossasset.ems.posttrade.surveillance.detectors.WashTradeDetector;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 12.15: each baseline detector catches its pattern AND stays quiet on innocent flow; alerts are
 * deterministic under replay and deduped at the feed.
 */
class SurveillanceFeedTest {

  private static final long WINDOW = 60_000_000L; // 60s

  private static SurveillanceEvent ev(
      String id, Type type, String actor, int side, long qty, long px, long ts) {
    return new SurveillanceEvent(id, type, actor, "AAPL", side, qty, px, ts);
  }

  @Test
  void layering_cancelHeavyOneSide_fillsOnTheOther_raises() {
    LayeringDetector detector = new LayeringDetector(WINDOW, 3);
    List<SurveillanceEvent> window = new ArrayList<>();
    // Pressure: 4 buy-side cancels; intent: 1 sell execution.
    for (int i = 1; i <= 4; i++) {
      window.add(ev("N" + i, Type.NEW_ORDER, "trader-x", 1, 1_000, 1_000_000, i * 100L));
      window.add(ev("C" + i, Type.CANCEL, "trader-x", 1, 1_000, 1_000_000, i * 100L + 50));
    }
    window.add(ev("X1", Type.EXECUTION, "trader-x", 2, 500, 1_000_500, 500L));

    List<Alert> alerts = detector.evaluate(window);
    assertThat(alerts).hasSize(1);
    Alert alert = alerts.get(0);
    assertThat(alert.severity()).isEqualTo(Alert.Severity.MEDIUM);
    assertThat(alert.subjectActor()).isEqualTo("trader-x");
    assertThat(alert.subjectEvents()).contains("C1", "C2", "C3", "C4", "X1");
    assertThat(alert.rationale()).contains("4 cancels side 1");
  }

  @Test
  void layering_quietOnInnocentFlow_cancelsWithoutOppositeExecutions() {
    LayeringDetector detector = new LayeringDetector(WINDOW, 3);
    List<SurveillanceEvent> window = new ArrayList<>();
    for (int i = 1; i <= 5; i++) {
      window.add(ev("C" + i, Type.CANCEL, "trader-y", 1, 1_000, 1_000_000, i * 100L));
    }
    // Cancels alone (changed your mind 5 times) is not layering — no opposite-side fills.
    assertThat(detector.evaluate(window)).isEmpty();
  }

  @Test
  void washTrade_sameActorBothSidesAtOverlappingPrice_raisesHigh() {
    WashTradeDetector detector = new WashTradeDetector(WINDOW, 100L);
    List<SurveillanceEvent> window =
        List.of(
            ev("B1", Type.EXECUTION, "trader-w", 1, 500, 1_000_000, 100L),
            ev("S1", Type.EXECUTION, "trader-w", 2, 500, 1_000_050, 200L));
    List<Alert> alerts = detector.evaluate(window);
    assertThat(alerts).hasSize(1);
    assertThat(alerts.get(0).severity()).isEqualTo(Alert.Severity.HIGH);
    assertThat(alerts.get(0).subjectEvents()).containsExactly("B1", "S1");
  }

  @Test
  void washTrade_quietWhenPricesDoNotOverlap_orActorsDiffer() {
    WashTradeDetector detector = new WashTradeDetector(WINDOW, 100L);
    // Same actor, prices 5 ticks apart — a legitimate round trip.
    assertThat(
            detector.evaluate(
                List.of(
                    ev("B1", Type.EXECUTION, "trader-w", 1, 500, 1_000_000, 100L),
                    ev("S1", Type.EXECUTION, "trader-w", 2, 500, 1_005_000, 200L))))
        .isEmpty();
    // Overlapping prices, different actors — that's just a market.
    assertThat(
            detector.evaluate(
                List.of(
                    ev("B1", Type.EXECUTION, "trader-a", 1, 500, 1_000_000, 100L),
                    ev("S1", Type.EXECUTION, "trader-b", 2, 500, 1_000_000, 200L))))
        .isEmpty();
  }

  @Test
  void fatFingerCluster_outlierOrdersVsOwnMedian_pagesCritical() {
    FatFingerClusterDetector detector = new FatFingerClusterDetector(WINDOW, 10, 2);
    List<SurveillanceEvent> window = new ArrayList<>();
    for (int i = 1; i <= 5; i++) {
      window.add(ev("N" + i, Type.NEW_ORDER, "trader-f", 1, 100, 1_000_000, i * 100L));
    }
    window.add(ev("F1", Type.NEW_ORDER, "trader-f", 1, 5_000, 1_000_000, 600L));
    window.add(ev("F2", Type.NEW_ORDER, "trader-f", 1, 8_000, 1_000_000, 700L));

    List<Alert> alerts = detector.evaluate(window);
    assertThat(alerts).hasSize(1);
    assertThat(alerts.get(0).severity()).isEqualTo(Alert.Severity.CRITICAL);
    assertThat(alerts.get(0).subjectEvents()).containsExactly("F1", "F2");

    // One outlier is a fat finger; a cluster needs minOutliers — quiet below it.
    window.remove(window.size() - 1);
    assertThat(detector.evaluate(window)).isEmpty();
  }

  @Test
  void feed_windowsByEventTime_dedupsByContentId_andReplayIsIdentical() {
    List<Alert> sunk = new ArrayList<>();
    SurveillanceFeed feed = new SurveillanceFeed(sunk::add);
    feed.register(new LayeringDetector(WINDOW, 3));

    List<SurveillanceEvent> events = new ArrayList<>();
    for (int i = 1; i <= 4; i++) {
      events.add(ev("N" + i, Type.NEW_ORDER, "trader-x", 1, 1_000, 1_000_000, i * 100L));
      events.add(ev("C" + i, Type.CANCEL, "trader-x", 1, 1_000, 1_000_000, i * 100L + 50));
    }
    events.add(ev("X1", Type.EXECUTION, "trader-x", 2, 500, 1_000_500, 900L));
    // Late event ARRIVES after but evaluation stays event-time-windowed and dedups: the same
    // pattern must not re-alert on every subsequent ingest.
    events.add(ev("N9", Type.NEW_ORDER, "trader-x", 1, 1_000, 1_000_000, 1_000L));

    events.forEach(feed::ingest);
    assertThat(feed.alerts()).hasSize(1);
    assertThat(sunk).hasSize(1);
    assertThat(feed.exportStream()).hasSize(events.size());

    // Replay determinism: a fresh feed over the same stream raises the identical alert id.
    SurveillanceFeed replay = new SurveillanceFeed(a -> {});
    replay.register(new LayeringDetector(WINDOW, 3));
    events.forEach(replay::ingest);
    assertThat(replay.alerts().get(0).alertId()).isEqualTo(feed.alerts().get(0).alertId());
  }
}
