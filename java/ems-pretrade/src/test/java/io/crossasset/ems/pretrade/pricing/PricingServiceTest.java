/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.pretrade.pricing.PricingService.FallbackPolicy;
import io.crossasset.ems.pretrade.pricing.PricingService.PriceKind;
import io.crossasset.ems.pretrade.pricing.PricingService.PricedValue;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link PricingService}: fallback-chain walking with freshness, model dispatch with
 * version capture, audited manual marks, source provenance on every result. Per
 * arch-pricing-service.md, task 10.8.
 */
class PricingServiceTest {

  private static final String FIGI = "BBG000BLNNH6";
  private static final FallbackPolicy POLICY = FallbackPolicy.conservative(5_000L, 60_000L);

  private final PricingService pricing = new PricingService();

  @Test
  void freshLive_winsTheChain() {
    pricing.recordLive(FIGI, 1_000, 9_000L);
    pricing.recordPrevClose(FIGI, 900);
    PricedValue px = pricing.priceWithFallback(FIGI, POLICY, 10_000L);
    assertThat(px.kind()).isEqualTo(PriceKind.LIVE);
    assertThat(px.px()).isEqualTo(1_000);
    assertThat(px.source()).isEqualTo("live-l1-mid");
  }

  @Test
  void staleLive_fallsToLastTrade() {
    pricing.recordLive(FIGI, 1_000, 0L); // 10s old vs 5s freshness
    pricing.recordLastTrade(FIGI, 990, 8_000L);
    PricedValue px = pricing.priceWithFallback(FIGI, POLICY, 10_000L);
    assertThat(px.kind()).isEqualTo(PriceKind.LAST_TRADE);
    assertThat(px.px()).isEqualTo(990);
  }

  @Test
  void staleEverything_fallsToPrevClose() {
    pricing.recordLive(FIGI, 1_000, 0L);
    pricing.recordLastTrade(FIGI, 990, 0L);
    pricing.recordPrevClose(FIGI, 950);
    PricedValue px = pricing.priceWithFallback(FIGI, POLICY, 100_000L);
    assertThat(px.kind()).isEqualTo(PriceKind.PREV_CLOSE);
    assertThat(px.px()).isEqualTo(950);
  }

  @Test
  void registeredModel_servesIndicativeWithVersionedProvenance() {
    pricing.registerModel(
        FIGI,
        new PricingService.PricingModel() {
          @Override
          public String modelId() {
            return "bond-ytm";
          }

          @Override
          public int version() {
            return 3;
          }

          @Override
          public Optional<Long> price(String figi) {
            return Optional.of(975L);
          }
        });
    PricedValue px = pricing.priceWithFallback(FIGI, POLICY, 10_000L);
    assertThat(px.kind()).isEqualTo(PriceKind.INDICATIVE);
    assertThat(px.px()).isEqualTo(975);
    assertThat(px.source()).isEqualTo("model:bond-ytm:v3");
  }

  @Test
  void storedIndicative_beatsModelDispatch() {
    pricing.recordIndicative(FIGI, 980, "curve-snap", 9_000L);
    pricing.registerModel(
        FIGI,
        new PricingService.PricingModel() {
          @Override
          public String modelId() {
            return "bond-ytm";
          }

          @Override
          public int version() {
            return 3;
          }

          @Override
          public Optional<Long> price(String figi) {
            return Optional.of(111L);
          }
        });
    PricedValue px = pricing.priceWithFallback(FIGI, POLICY, 10_000L);
    assertThat(px.px()).isEqualTo(980);
    assertThat(px.source()).isEqualTo("curve-snap");
  }

  @Test
  void upperBound_isTheConservativeTerminal() {
    pricing.recordUpperBound(FIGI, 2_000);
    PricedValue px = pricing.priceWithFallback(FIGI, POLICY, 10_000L);
    assertThat(px.kind()).isEqualTo(PriceKind.UPPER_BOUND);
    assertThat(px.px()).isEqualTo(2_000);
  }

  @Test
  void exhaustedChain_returnsNoneWithNullPx() {
    PricedValue px = pricing.priceWithFallback(FIGI, POLICY, 10_000L);
    assertThat(px.kind()).isEqualTo(PriceKind.NONE);
    assertThat(px.px()).isNull();
  }

  @Test
  void manualMark_journaledAndUsableInCustomChain() {
    pricing.recordManualMark(FIGI, 1_015, "trader-1", "illiquid; marked to comparable", 9_500L);
    FallbackPolicy manualFirst =
        new FallbackPolicy(List.of(PriceKind.MANUAL, PriceKind.PREV_CLOSE), 5_000L, 60_000L);
    PricedValue px = pricing.priceWithFallback(FIGI, manualFirst, 10_000L);
    assertThat(px.kind()).isEqualTo(PriceKind.MANUAL);
    assertThat(px.source()).isEqualTo("manual:trader-1");
    assertThat(pricing.manualMarkJournal()).hasSize(1);
    assertThat(pricing.manualMarkJournal().get(0).rationale()).contains("comparable");
  }

  @Test
  void chainOrder_isRespected() {
    pricing.recordLive(FIGI, 1_000, 9_999L);
    pricing.recordPrevClose(FIGI, 950);
    FallbackPolicy prevCloseFirst =
        new FallbackPolicy(List.of(PriceKind.PREV_CLOSE, PriceKind.LIVE), 5_000L, 60_000L);
    PricedValue px = pricing.priceWithFallback(FIGI, prevCloseFirst, 10_000L);
    assertThat(px.kind()).isEqualTo(PriceKind.PREV_CLOSE);
  }
}
