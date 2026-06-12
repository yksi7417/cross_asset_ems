/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.rest;

import io.crossasset.ems.instrument.AssetClass;
import io.crossasset.ems.instrument.CurrencyCode;
import io.crossasset.ems.instrument.Fungibility;
import io.crossasset.ems.instrument.InstrumentCore;
import io.crossasset.ems.instrument.InstrumentType;
import io.crossasset.ems.instrument.LifecycleStatus;
import io.crossasset.ems.instrument.SettlementConvention;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The trader-desktop demo instrument universe (18.21): cross-asset, at least two instruments per
 * supported asset class — one US, one international — so the demo exercises the same breadth the
 * post-trade spine proved in Phase 16 (CrossAssetSmokeTest).
 *
 * <p>Per-instrument trading conventions ride along: {@code lotQty} is the natural order-size unit
 * (shares for equity, $1k face for bonds, notional for FX/IRS, contracts for futures) and
 * {@code venues} are the MICs the demo bot routes to. Prices are fixed-point 1e4 (182_4500 =
 * 182.45); for IRS the "price" is the fixed rate in percent (4_0250 = 4.0250%).
 *
 * <p>Equity FIGIs are real; non-equity identifiers are synthetic demo FIGIs (clearly so — bonds,
 * FX pairs and swaps don't ship real FIGIs in this repo's mock security master).
 */
public final class DemoUniverse {

  /** One demo instrument: security-master core + trading conventions for the demo bot. */
  public record DemoInstrument(InstrumentCore core, long basePx, long lotQty, List<String> venues) {
    public String figi() {
      return core.figi();
    }

    public String name() {
      return core.displayName();
    }

    public AssetClass assetClass() {
      return core.assetClass();
    }
  }

  public static final List<DemoInstrument> INSTRUMENTS =
      List.of(
          // ── Equity ──────────────────────────────────────────────────────────────
          new DemoInstrument(
              core("BBG000B9XRY4", "Apple Inc", InstrumentType.COMMON_STOCK, CurrencyCode.USD,
                  "US", SettlementConvention.T_PLUS_1),
              182_4500L, 100L, List.of("XNAS", "XNYS", "ARCX")),
          new DemoInstrument(
              core("BBG000BPH459", "Microsoft Corp", InstrumentType.COMMON_STOCK, CurrencyCode.USD,
                  "US", SettlementConvention.T_PLUS_1),
              415_1200L, 100L, List.of("XNAS", "XNYS", "ARCX")),
          new DemoInstrument(
              core("BBG000BCM915", "Toyota Motor Corp", InstrumentType.COMMON_STOCK,
                  CurrencyCode.JPY, "JP", SettlementConvention.T_PLUS_2),
              2815_0000L, 100L, List.of("XTKS")),
          // ── Government bonds ───────────────────────────────────────────────────
          new DemoInstrument(
              core("BBG00DEMOT35", "US Treasury 4.25% 2035", InstrumentType.TREASURY,
                  CurrencyCode.USD, "US", SettlementConvention.T_PLUS_1),
              98_7500L, 100L, List.of("BTEC", "DWFI")),
          new DemoInstrument(
              core("BBG00DEMOG34", "UK Gilt 4.5% 2034", InstrumentType.TREASURY,
                  CurrencyCode.GBP, "GB", SettlementConvention.T_PLUS_1),
              96_2000L, 100L, List.of("TWEU")),
          // ── Corporate credit ───────────────────────────────────────────────────
          new DemoInstrument(
              core("BBG00DEMOC29", "Apple Inc 3.45% 2029", InstrumentType.CORPORATE_SENIOR,
                  CurrencyCode.USD, "US", SettlementConvention.T_PLUS_1),
              97_1000L, 100L, List.of("MKAX")),
          new DemoInstrument(
              core("BBG00DEMOC31", "Volkswagen 4.125% 2031", InstrumentType.CORPORATE_SENIOR,
                  CurrencyCode.EUR, "DE", SettlementConvention.T_PLUS_2),
              99_4000L, 100L, List.of("MAEL")),
          // ── FX (spot + forward) ────────────────────────────────────────────────
          new DemoInstrument(
              core("BBG00DEMOFX1", "EUR/USD Spot", InstrumentType.FX_SPOT,
                  CurrencyCode.USD, "US", SettlementConvention.T_PLUS_2),
              1_0842L, 100_000L, List.of("FXAL", "EBSX")),
          new DemoInstrument(
              core("BBG00DEMOFX2", "USD/JPY 1M Forward", InstrumentType.FX_FORWARD,
                  CurrencyCode.JPY, "JP", SettlementConvention.T_PLUS_2_AFTER_FIXING),
              156_2500L, 100_000L, List.of("FXAL")),
          // ── Listed derivatives ─────────────────────────────────────────────────
          new DemoInstrument(
              core("BBG00DEMOES6", "E-mini S&P 500 Sep26", InstrumentType.LISTED_FUTURE,
                  CurrencyCode.USD, "US", SettlementConvention.PER_CCP),
              5300_2500L, 1L, List.of("XCME")),
          new DemoInstrument(
              core("BBG00DEMOSX6", "EURO STOXX 50 Sep26", InstrumentType.LISTED_FUTURE,
                  CurrencyCode.EUR, "DE", SettlementConvention.PER_CCP),
              4925_0000L, 1L, List.of("XEUR")),
          // ── Rates (IRS) ────────────────────────────────────────────────────────
          new DemoInstrument(
              core("BBG00DEMOIR5", "USD SOFR IRS 5Y", InstrumentType.VANILLA_IRS,
                  CurrencyCode.USD, "US", SettlementConvention.PER_CCP),
              4_0250L, 1_000_000L, List.of("TWSD")),
          new DemoInstrument(
              core("BBG00DEMOIR6", "EUR ESTR IRS 5Y", InstrumentType.VANILLA_IRS,
                  CurrencyCode.EUR, "DE", SettlementConvention.PER_CCP),
              2_6150L, 1_000_000L, List.of("BGCD")));

  /** figi → trade currency, for P&L conversion to the firm base currency. */
  public static final Map<String, String> CURRENCY_OF =
      INSTRUMENTS.stream()
          .collect(Collectors.toUnmodifiableMap(
              DemoInstrument::figi, i -> i.core().currency().name()));

  /** Demo FX rates: USD per 1 unit of currency, fixed-point 4dp (PnlService.setFxRate). */
  public static final Map<String, Long> FX_TO_USD =
      Map.of("EUR", 1_0842L, "GBP", 1_2710L, "JPY", 64L);

  private DemoUniverse() {}

  private static InstrumentCore core(
      String figi,
      String name,
      InstrumentType type,
      CurrencyCode currency,
      String country,
      SettlementConvention settlement) {
    return new InstrumentCore(
        figi,
        "IID-" + figi,
        null,
        null,
        type.assetClass,
        type,
        name,
        name,
        null,
        currency,
        country,
        null,
        Fungibility.FUNGIBLE,
        settlement,
        0,
        LifecycleStatus.ACTIVE,
        1_000_000L,
        Long.MAX_VALUE,
        1L,
        null,
        1_000_000L,
        1_000_000L);
  }
}
