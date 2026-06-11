/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.pnl;

import io.crossasset.ems.pretrade.position.Position;
import io.crossasset.ems.pretrade.position.PositionService;
import io.crossasset.ems.pretrade.pricing.PricingService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Intraday P&L (task 18.7): realized off the position books (10.7), unrealized off the pricing
 * fallback chain (10.8) with the chosen mark source captured per row, and everything FX-converted
 * into the firm's base currency for the desk-level number a head trader actually watches.
 *
 * <p>Nothing silent: a position the chain cannot mark still reports its realized leg and is counted
 * {@code unmarked}; a currency without an FX rate keeps its local values, is excluded from base
 * totals, and is counted {@code unconverted}. Both flags surface on the report and per row.
 *
 * <p>Money values are fixed-point (4dp) like prices; FX rates are fixed-point 4dp units of base per
 * unit of local currency (EURUSD 1.0852 → 10_852 with USD base).
 */
public final class PnlService {

  /** One position's P&L line. Local values always present; base values when an FX rate exists. */
  public record PositionPnl(
      String account,
      String figi,
      String currency,
      long netQty,
      long avgCost,
      @Nullable Long markPx,
      String markSource,
      long realizedLocal,
      @Nullable Long unrealizedLocal,
      @Nullable Long realizedBase,
      @Nullable Long unrealizedBase) {

    /** Marked and converted — contributes fully to base totals. */
    public boolean inBaseTotals() {
      return realizedBase != null;
    }
  }

  /** The intraday snapshot. */
  public record PnlReport(
      String baseCurrency,
      long asOfMillis,
      List<PositionPnl> rows,
      long totalRealizedBase,
      long totalUnrealizedBase,
      long totalBase,
      int unmarked,
      int unconverted) {}

  private final PositionService positions;
  private final PricingService pricing;
  private final Function<String, String> currencyOf;
  private final String baseCurrency;
  private final Map<String, Long> fxRates = new LinkedHashMap<>();

  /**
   * @param currencyOf resolves an instrument to its trading currency (security-master-backed)
   * @param baseCurrency the firm reporting currency; its own rate is identity
   */
  public PnlService(
      PositionService positions,
      PricingService pricing,
      Function<String, String> currencyOf,
      String baseCurrency) {
    this.positions = Objects.requireNonNull(positions, "positions");
    this.pricing = Objects.requireNonNull(pricing, "pricing");
    this.currencyOf = Objects.requireNonNull(currencyOf, "currencyOf");
    this.baseCurrency = Objects.requireNonNull(baseCurrency, "baseCurrency");
  }

  /** Set the base-per-unit rate for one currency (fixed-point 4dp). */
  public synchronized void setFxRate(String currency, long rateFp) {
    if (rateFp <= 0) {
      throw new IllegalArgumentException("rateFp must be positive: " + rateFp);
    }
    fxRates.put(currency, rateFp);
  }

  /** Intraday snapshot over the given accounts with the caller's marking policy. */
  public synchronized PnlReport snapshot(
      List<String> accounts, PricingService.FallbackPolicy policy, long nowMillis) {
    List<PositionPnl> rows = new ArrayList<>();
    long totalRealized = 0;
    long totalUnrealized = 0;
    int unmarked = 0;
    int unconverted = 0;

    for (String account : accounts) {
      for (Position flatPosition : positions.tradedPositionsForAccount(account)) {
        PricingService.PricedValue mark =
            pricing.priceWithFallback(flatPosition.figi(), policy, nowMillis);
        Long markPx = mark.kind() == PricingService.PriceKind.NONE ? null : mark.px();
        Position position = positions.position(account, flatPosition.figi(), markPx);

        String currency = currencyOf.apply(position.figi());
        Long rate = baseCurrency.equals(currency) ? Long.valueOf(10_000L) : fxRates.get(currency);

        Long unrealizedLocal = position.unrealizedPnl();
        Long realizedBase = rate == null ? null : convert(position.realizedPnl(), rate);
        Long unrealizedBase =
            rate == null || unrealizedLocal == null ? null : convert(unrealizedLocal, rate);

        if (markPx == null) {
          unmarked++;
        }
        if (rate == null) {
          unconverted++;
        } else {
          totalRealized += realizedBase;
          if (unrealizedBase != null) {
            totalUnrealized += unrealizedBase;
          }
        }
        rows.add(
            new PositionPnl(
                account,
                position.figi(),
                currency,
                position.netQty(),
                position.avgCost(),
                markPx,
                mark.kind().name() + ":" + mark.source(),
                position.realizedPnl(),
                unrealizedLocal,
                realizedBase,
                unrealizedBase));
      }
    }
    return new PnlReport(
        baseCurrency,
        nowMillis,
        List.copyOf(rows),
        totalRealized,
        totalUnrealized,
        totalRealized + totalUnrealized,
        unmarked,
        unconverted);
  }

  private static long convert(long localFp, long rateFp) {
    return localFp * rateFp / 10_000;
  }
}
