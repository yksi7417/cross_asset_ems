/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.crossasset.ems.instrument.AssetClass;
import io.crossasset.ems.pretrade.analytics.PreTradeAnalytics.MarketSnapshot;
import io.crossasset.ems.pretrade.analytics.PreTradeAnalytics.OrderIntent;
import io.crossasset.ems.pretrade.analytics.PreTradeAnalytics.Recommendation;
import io.crossasset.ems.pretrade.analytics.PreTradeAnalytics.SquareRootImpactModel;
import io.crossasset.ems.pretrade.analytics.PreTradeAnalytics.Urgency;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link PreTradeAnalytics}: asset-class dispatch, square-root impact math,
 * participation-banded strategy ranking, degradation with cautions, replayable envelopes. Per
 * arch-pretrade-analytics.md, task 10.9.
 */
class PreTradeAnalyticsTest {

  private final PreTradeAnalytics analytics = new PreTradeAnalytics();

  @Test
  void dispatch_byAssetClass() {
    analytics.register(new SquareRootImpactModel(Set.of(AssetClass.EQUITY)));
    assertThat(analytics.recommend(intent(AssetClass.EQUITY, 10_000), snapshot(1_000_000L), 1L))
        .isPresent();
    assertThat(analytics.recommend(intent(AssetClass.FX, 10_000), snapshot(1_000_000L), 1L))
        .isEmpty();
  }

  @Test
  void squareRootImpact_math() {
    analytics.register(new SquareRootImpactModel(Set.of(AssetClass.EQUITY)));
    // vol 20 bps, qty 10k vs ADV 1M -> 20 * sqrt(0.01) = 2 bps
    Recommendation rec =
        analytics
            .recommend(intent(AssetClass.EQUITY, 10_000), snapshot(1_000_000L), 1L)
            .orElseThrow();
    assertThat(rec.costEstimateBps()).isCloseTo(2.0, within(1e-9));
  }

  @Test
  void lowParticipation_recommendsVwapFirst() {
    analytics.register(new SquareRootImpactModel(Set.of(AssetClass.EQUITY)));
    Recommendation rec =
        analytics
            .recommend(intent(AssetClass.EQUITY, 10_000), snapshot(1_000_000L), 1L)
            .orElseThrow();
    assertThat(rec.strategyAdvice().get(0).strategy()).isEqualTo("ALGO_VWAP");
    assertThat(rec.cautions()).isEmpty();
  }

  @Test
  void hugeParticipation_recommendsDarkFirstWithCaution() {
    analytics.register(new SquareRootImpactModel(Set.of(AssetClass.EQUITY)));
    Recommendation rec =
        analytics
            .recommend(intent(AssetClass.EQUITY, 400_000), snapshot(1_000_000L), 1L)
            .orElseThrow();
    assertThat(rec.strategyAdvice().get(0).strategy()).isEqualTo("DARK_FIRST");
    assertThat(rec.cautions()).anyMatch(c -> c.contains("25%"));
  }

  @Test
  void missingInputs_degradesWithCautionNotSilence() {
    analytics.register(new SquareRootImpactModel(Set.of(AssetClass.EQUITY)));
    Recommendation rec =
        analytics
            .recommend(
                intent(AssetClass.EQUITY, 10_000),
                new MarketSnapshot(1_000L, null, null, null, 5L),
                1L)
            .orElseThrow();
    assertThat(rec.costEstimateBps()).isNull();
    assertThat(rec.cautions()).anyMatch(c -> c.contains("insufficient inputs"));
    assertThat(rec.strategyAdvice().get(0).strategy()).isEqualTo("MANUAL_REVIEW");
  }

  @Test
  void highUrgency_addsCaution() {
    analytics.register(new SquareRootImpactModel(Set.of(AssetClass.EQUITY)));
    Recommendation rec =
        analytics
            .recommend(
                new OrderIntent(
                    "BBG000BLNNH6", AssetClass.EQUITY, 1, 10_000, Urgency.HIGH, "acc-1"),
                snapshot(1_000_000L),
                1L)
            .orElseThrow();
    assertThat(rec.cautions()).anyMatch(c -> c.contains("urgency"));
  }

  @Test
  void envelope_capturesModelVersionAndInputs() {
    analytics.register(new SquareRootImpactModel(Set.of(AssetClass.EQUITY)));
    MarketSnapshot snapshot = snapshot(1_000_000L);
    Recommendation rec =
        analytics.recommend(intent(AssetClass.EQUITY, 10_000), snapshot, 42L).orElseThrow();
    assertThat(rec.modelId()).isEqualTo("sqrt-impact");
    assertThat(rec.modelVersion()).isEqualTo(1);
    assertThat(rec.generatedAtMillis()).isEqualTo(42L);
    assertThat(rec.inputsUsed()).isEqualTo(snapshot);
  }

  @Test
  void firstMatchingModel_wins() {
    analytics.register(new SquareRootImpactModel(Set.of(AssetClass.EQUITY)));
    analytics.register(
        new PreTradeAnalytics.PreTradeModel() {
          @Override
          public String modelId() {
            return "second";
          }

          @Override
          public int version() {
            return 1;
          }

          @Override
          public Set<AssetClass> assetClasses() {
            return Set.of(AssetClass.EQUITY);
          }

          @Override
          public Recommendation evaluate(
              OrderIntent intent, MarketSnapshot snapshot, long nowMillis) {
            return new Recommendation(
                "second", 1, nowMillis, null, java.util.List.of(), java.util.List.of(), snapshot);
          }
        });
    Optional<Recommendation> rec =
        analytics.recommend(intent(AssetClass.EQUITY, 10_000), snapshot(1_000_000L), 1L);
    assertThat(rec.orElseThrow().modelId()).isEqualTo("sqrt-impact");
  }

  private static OrderIntent intent(AssetClass assetClass, long qty) {
    return new OrderIntent("BBG000BLNNH6", assetClass, 1, qty, Urgency.MEDIUM, "acc-1");
  }

  private static MarketSnapshot snapshot(Long adv) {
    return new MarketSnapshot(1_000L, adv, 20.0, 5.0, 5L);
  }
}
