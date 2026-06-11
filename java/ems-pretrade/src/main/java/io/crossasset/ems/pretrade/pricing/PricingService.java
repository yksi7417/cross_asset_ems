/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.pricing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Valuation pricing service (task 10.8, arch-pricing-service.md) — distinct from live quotes:
 * fair-value / indicative / mark pricing with the <b>fallback chain</b> that compliance (fat-finger
 * reference), risk (marks), and positions (unrealized P&L) consume. The chosen source is always
 * captured on the result so the consuming check event can reconstruct what the decision saw.
 *
 * <p>Sources: LIVE mid (fresh within policy), LAST_TRADE (within tolerance), PREV_CLOSE, INDICATIVE
 * (stored, or computed by a registered pluggable versioned {@link PricingModel}), MANUAL marks
 * (trader overrides — always audited), CONSERVATIVE upper bound (the block-path terminal for
 * conservative firms). The chain itself is policy: per asset class / instrument tier per firm,
 * passed in by the caller (configuration-service wiring later).
 *
 * <p>Curve/surface construction and the full model library (Black-Scholes, OAS, CDS, IRS, iNAV)
 * plug in behind {@link PricingModel} as they are built; this slice carries the registry, dispatch,
 * fallback semantics, and audit.
 */
public final class PricingService {

  /** Price source kinds, in the vocabulary of the arch-compliance fallback chain. */
  public enum PriceKind {
    LIVE,
    LAST_TRADE,
    PREV_CLOSE,
    INDICATIVE,
    MANUAL,
    UPPER_BOUND,
    NONE
  }

  /** A resolved price with its provenance; {@code px} is null only when kind is NONE. */
  public record PricedValue(PriceKind kind, @Nullable Long px, long asOfMillis, String source) {}

  /** Pluggable, versioned valuation model (arch § Model registry). */
  public interface PricingModel {
    String modelId();

    int version();

    Optional<Long> price(String figi);
  }

  /** The walk order + freshness tolerances for one asset-class/tier/firm policy. */
  public record FallbackPolicy(
      List<PriceKind> chain, long liveFreshnessMillis, long lastTradeToleranceMillis) {
    public FallbackPolicy {
      chain = List.copyOf(Objects.requireNonNull(chain, "chain"));
    }

    /** A conservative default: live → last trade → prev close → indicative → upper bound. */
    public static FallbackPolicy conservative(long liveFreshness, long lastTradeTolerance) {
      return new FallbackPolicy(
          List.of(
              PriceKind.LIVE,
              PriceKind.LAST_TRADE,
              PriceKind.PREV_CLOSE,
              PriceKind.INDICATIVE,
              PriceKind.UPPER_BOUND),
          liveFreshness,
          lastTradeTolerance);
    }
  }

  /** One audited manual mark. */
  public record ManualMark(String figi, long px, String trader, String rationale, long atMillis) {}

  private record Stamped(long px, long atMillis, String source) {}

  private final ConcurrentHashMap<String, Stamped> live = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Stamped> lastTrade = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Stamped> prevClose = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Stamped> indicative = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Stamped> upperBound = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Stamped> manual = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, PricingModel> models = new ConcurrentHashMap<>();
  private final List<ManualMark> manualMarkJournal =
      java.util.Collections.synchronizedList(new ArrayList<>());

  // ── Ingestion ────────────────────────────────────────────────────────────────

  public void recordLive(String figi, long mid, long atMillis) {
    live.put(figi, new Stamped(mid, atMillis, "live-l1-mid"));
  }

  public void recordLastTrade(String figi, long px, long atMillis) {
    lastTrade.put(figi, new Stamped(px, atMillis, "last-trade"));
  }

  public void recordPrevClose(String figi, long px) {
    prevClose.put(figi, new Stamped(px, 0, "prev-close"));
  }

  public void recordIndicative(String figi, long px, String source, long atMillis) {
    indicative.put(figi, new Stamped(px, atMillis, source));
  }

  public void recordUpperBound(String figi, long px) {
    upperBound.put(figi, new Stamped(px, 0, "conservative-upper-bound"));
  }

  /** Trader override — always journaled with identity + rationale. */
  public void recordManualMark(
      String figi, long px, String trader, String rationale, long atMillis) {
    Objects.requireNonNull(rationale, "rationale");
    manual.put(figi, new Stamped(px, atMillis, "manual:" + trader));
    manualMarkJournal.add(new ManualMark(figi, px, trader, rationale, atMillis));
  }

  /** Register the valuation model for an instrument (versioned; replaces any prior). */
  public void registerModel(String figi, PricingModel model) {
    models.put(figi, model);
  }

  public List<ManualMark> manualMarkJournal() {
    synchronized (manualMarkJournal) {
      return List.copyOf(manualMarkJournal);
    }
  }

  // ── Resolution ───────────────────────────────────────────────────────────────

  /**
   * Walk the policy's chain and return the first available source, with provenance. {@code
   * PriceKind.NONE} (null px) when the chain is exhausted — callers decide whether to block (policy
   * {@code block_on_no_reference}) or warn.
   */
  public PricedValue priceWithFallback(String figi, FallbackPolicy policy, long nowMillis) {
    for (PriceKind kind : policy.chain()) {
      PricedValue resolved =
          switch (kind) {
            case LIVE -> fresh(live.get(figi), kind, nowMillis, policy.liveFreshnessMillis());
            case LAST_TRADE ->
                fresh(lastTrade.get(figi), kind, nowMillis, policy.lastTradeToleranceMillis());
            case PREV_CLOSE -> always(prevClose.get(figi), kind);
            case INDICATIVE -> indicative(figi);
            case MANUAL -> always(manual.get(figi), kind);
            case UPPER_BOUND -> always(upperBound.get(figi), kind);
            case NONE -> null;
          };
      if (resolved != null) {
        return resolved;
      }
    }
    return new PricedValue(PriceKind.NONE, null, nowMillis, "chain-exhausted");
  }

  private static @Nullable PricedValue fresh(
      @Nullable Stamped stamped, PriceKind kind, long nowMillis, long toleranceMillis) {
    if (stamped == null || nowMillis - stamped.atMillis() >= toleranceMillis) {
      return null;
    }
    return new PricedValue(kind, stamped.px(), stamped.atMillis(), stamped.source());
  }

  private static @Nullable PricedValue always(@Nullable Stamped stamped, PriceKind kind) {
    return stamped == null
        ? null
        : new PricedValue(kind, stamped.px(), stamped.atMillis(), stamped.source());
  }

  /** Stored indicative wins; otherwise dispatch to the instrument's registered model. */
  private @Nullable PricedValue indicative(String figi) {
    Stamped stored = indicative.get(figi);
    if (stored != null) {
      return new PricedValue(PriceKind.INDICATIVE, stored.px(), stored.atMillis(), stored.source());
    }
    PricingModel model = models.get(figi);
    if (model == null) {
      return null;
    }
    return model
        .price(figi)
        .map(
            px ->
                new PricedValue(
                    PriceKind.INDICATIVE,
                    px,
                    0,
                    "model:" + model.modelId() + ":v" + model.version()))
        .orElse(null);
  }
}
