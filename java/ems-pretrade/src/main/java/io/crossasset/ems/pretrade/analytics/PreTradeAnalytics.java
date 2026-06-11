/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.analytics;

import io.crossasset.ems.instrument.AssetClass;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;

/**
 * Pre-trade analytics (task 10.9, arch-pretrade-analytics.md): a pluggable, versioned,
 * asset-class-scoped quant model registry returning <b>advisory</b> recommendations — cost
 * estimates, ranked strategy advice, cautions. Recommendations inform SOR strategy selection and
 * trader pre-flight reasoning; they never block (validator → compliance → risk still gate).
 *
 * <p>Determinism: the market snapshot is an explicit input and is echoed back on the recommendation
 * ({@code inputsUsed}) with the model id+version, so replay reproduces the advice byte-for-byte.
 * Dispatch picks the first registered model covering the intent's asset class; firm-policy /
 * TCA-performance ranking between competing models arrives with 12.14.
 */
public final class PreTradeAnalytics {

  /** Trader-declared urgency, an input to strategy ranking. */
  public enum Urgency {
    LOW,
    MEDIUM,
    HIGH
  }

  /** The order intent under evaluation; side uses FIX tag 54. */
  public record OrderIntent(
      String figi, AssetClass assetClass, int side, long qty, Urgency urgency, String account) {}

  /**
   * Caller-supplied market state: mark price, average daily volume, daily volatility and current
   * spread in basis points. Nullable fields = unavailable; models must degrade with cautions.
   */
  public record MarketSnapshot(
      @Nullable Long markPx,
      @Nullable Long adv,
      @Nullable Double volatilityBps,
      @Nullable Double spreadBps,
      long asOfMillis) {}

  /** One ranked strategy suggestion. */
  public record StrategyAdvice(
      String strategy, double score, @Nullable Double expectedCostBps, String rationale) {}

  /** The advisory envelope; {@code inputsUsed} + model version make it replayable. */
  public record Recommendation(
      String modelId,
      int modelVersion,
      long generatedAtMillis,
      @Nullable Double costEstimateBps,
      List<StrategyAdvice> strategyAdvice,
      List<String> cautions,
      MarketSnapshot inputsUsed) {
    public Recommendation {
      strategyAdvice = List.copyOf(strategyAdvice);
      cautions = List.copyOf(cautions);
    }
  }

  /** Pluggable model SPI: versioned and asset-class-scoped. */
  public interface PreTradeModel {
    String modelId();

    int version();

    Set<AssetClass> assetClasses();

    Recommendation evaluate(OrderIntent intent, MarketSnapshot snapshot, long nowMillis);
  }

  private final CopyOnWriteArrayList<PreTradeModel> models = new CopyOnWriteArrayList<>();

  public void register(PreTradeModel model) {
    models.add(Objects.requireNonNull(model, "model"));
  }

  /** Dispatch to the first registered model covering the intent's asset class. */
  public Optional<Recommendation> recommend(
      OrderIntent intent, MarketSnapshot snapshot, long nowMillis) {
    for (PreTradeModel model : models) {
      if (model.assetClasses().contains(intent.assetClass())) {
        return Optional.of(model.evaluate(intent, snapshot, nowMillis));
      }
    }
    return Optional.empty();
  }

  // ── Reference model ──────────────────────────────────────────────────────────

  /**
   * Square-root market-impact reference model: {@code cost_bps = volatility_bps × sqrt(qty / ADV)},
   * with participation-banded strategy ranking. Proves the SPI shape; the fuller library
   * (Almgren-Chriss trajectories, liquidity/spread forecasts) registers alongside.
   */
  public static final class SquareRootImpactModel implements PreTradeModel {

    private final Set<AssetClass> scope;

    public SquareRootImpactModel(Set<AssetClass> scope) {
      this.scope = Set.copyOf(scope);
    }

    @Override
    public String modelId() {
      return "sqrt-impact";
    }

    @Override
    public int version() {
      return 1;
    }

    @Override
    public Set<AssetClass> assetClasses() {
      return scope;
    }

    @Override
    public Recommendation evaluate(OrderIntent intent, MarketSnapshot snapshot, long nowMillis) {
      List<String> cautions = new ArrayList<>();
      if (snapshot.adv() == null || snapshot.volatilityBps() == null) {
        cautions.add("insufficient inputs: ADV/volatility unavailable — no cost estimate");
        return new Recommendation(
            modelId(),
            version(),
            nowMillis,
            null,
            List.of(new StrategyAdvice("MANUAL_REVIEW", 0.5, null, "model inputs missing")),
            cautions,
            snapshot);
      }
      double participation = (double) intent.qty() / snapshot.adv();
      double costBps = snapshot.volatilityBps() * Math.sqrt(participation);

      List<StrategyAdvice> advice = new ArrayList<>();
      if (participation < 0.05) {
        advice.add(new StrategyAdvice("ALGO_VWAP", 0.9, costBps, "low participation; ride volume"));
        advice.add(
            new StrategyAdvice("ALGO_IS", 0.7, costBps * 1.1, "arrival-anchored alternative"));
      } else if (participation <= 0.25) {
        advice.add(
            new StrategyAdvice("ALGO_IS", 0.8, costBps, "moderate participation; manage arrival"));
        advice.add(new StrategyAdvice("ALGO_POV", 0.7, costBps * 1.05, "participate with volume"));
        cautions.add("elevated participation: " + Math.round(participation * 100) + "% of ADV");
      } else {
        advice.add(
            new StrategyAdvice(
                "DARK_FIRST", 0.8, costBps * 0.9, "minimize footprint before lit venues"));
        advice.add(new StrategyAdvice("ALGO_POV_SLOW", 0.6, costBps, "stretch the horizon"));
        cautions.add(
            "order exceeds 25% of ADV (" + Math.round(participation * 100) + "%) — expect impact");
      }
      if (intent.urgency() == Urgency.HIGH) {
        cautions.add("high urgency raises expected cost above the passive estimate");
      }
      return new Recommendation(
          modelId(), version(), nowMillis, costBps, advice, cautions, snapshot);
    }
  }
}
