/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.rest;

import io.crossasset.ems.instrument.AssetClass;
import io.crossasset.ems.instrument.CurrencyCode;
import io.crossasset.ems.instrument.CurrencyProfile;
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
 * <p>Issuer (18.29): instruments carry {@code issuerLei} so group-by-issuer collapses one
 * company's whole capital structure — Microsoft's stock, convertible and listed option group
 * under the same node, Apple's equity with its '29 bond. Derivatives take the UNDERLYING's
 * issuer; products without a single issuer (FX, IRS, index futures) carry none. {@code
 * ISSUER_NAMES} is the demo LEI→name registry (a real deployment resolves against GLEIF).
 *
 * <p>Equity FIGIs are real; non-equity identifiers are synthetic demo FIGIs (clearly so — bonds,
 * FX pairs and swaps don't ship real FIGIs in this repo's mock security master). LEIs are
 * synthetic demo identifiers too.
 */
public final class DemoUniverse {

  private static final String LEI_AAPL = "LEI-DEMO-AAPL";
  private static final String LEI_MSFT = "LEI-DEMO-MSFT";
  private static final String LEI_TOYOTA = "LEI-DEMO-TOYOTA";
  private static final String LEI_VW = "LEI-DEMO-VW";
  private static final String LEI_UST = "LEI-DEMO-UST";
  private static final String LEI_UKGOV = "LEI-DEMO-UKGOV";
  private static final String LEI_SHELL = "LEI-DEMO-SHELL";

  /** Demo LEI → issuer display name (the GLEIF stand-in). */
  public static final Map<String, String> ISSUER_NAMES =
      Map.of(
          LEI_AAPL, "Apple Inc",
          LEI_MSFT, "Microsoft Corp",
          LEI_TOYOTA, "Toyota Motor Corp",
          LEI_VW, "Volkswagen AG",
          LEI_UST, "US Treasury",
          LEI_UKGOV, "UK Government",
          LEI_SHELL, "Shell plc");

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
                  "US", SettlementConvention.T_PLUS_1, LEI_AAPL),
              182_4500L, 100L, List.of("XNAS", "XNYS", "ARCX")),
          new DemoInstrument(
              core("BBG000BPH459", "Microsoft Corp", InstrumentType.COMMON_STOCK, CurrencyCode.USD,
                  "US", SettlementConvention.T_PLUS_1, LEI_MSFT),
              415_1200L, 100L, List.of("XNAS", "XNYS", "ARCX")),
          new DemoInstrument(
              core("BBG000BCM915", "Toyota Motor Corp", InstrumentType.COMMON_STOCK,
                  CurrencyCode.JPY, "JP", SettlementConvention.T_PLUS_2, LEI_TOYOTA),
              2815_0000L, 100L, List.of("XTKS")),
          // ADR (18.30): the receipt trades + settles USD on US venues; the underlying line
          // above is JPY on XTKS. Same issuer — group-by-issuer shows both wrappers.
          new DemoInstrument(
              core("BBG00DEMOADR", "Toyota Motor ADR", InstrumentType.ADR,
                  CurrencyCode.USD, "US", SettlementConvention.T_PLUS_1, LEI_TOYOTA),
              180_2000L, 100L, List.of("XNYS")),
          // Dual-listing minor-unit trap (18.30): Shell quotes in GBp (pence) on XLON — a
          // 2,650.5 print is £26.505. tradingMinorUnit=true in its CurrencyProfile.
          new DemoInstrument(
              core("BBG00DEMOSHL", "Shell plc", InstrumentType.COMMON_STOCK,
                  CurrencyCode.GBP, "GB", SettlementConvention.T_PLUS_2, LEI_SHELL),
              2650_5000L, 100L, List.of("XLON")),
          // ── Government bonds ───────────────────────────────────────────────────
          new DemoInstrument(
              core("BBG00DEMOT35", "US Treasury 4.25% 2035", InstrumentType.TREASURY,
                  CurrencyCode.USD, "US", SettlementConvention.T_PLUS_1, LEI_UST),
              98_7500L, 100L, List.of("BTEC", "DWFI")),
          new DemoInstrument(
              core("BBG00DEMOG34", "UK Gilt 4.5% 2034", InstrumentType.TREASURY,
                  CurrencyCode.GBP, "GB", SettlementConvention.T_PLUS_1, LEI_UKGOV),
              96_2000L, 100L, List.of("TWEU")),
          // ── Corporate credit (incl. Microsoft's convertible — issuer story) ────
          new DemoInstrument(
              core("BBG00DEMOC29", "Apple Inc 3.45% 2029", InstrumentType.CORPORATE_SENIOR,
                  CurrencyCode.USD, "US", SettlementConvention.T_PLUS_1, LEI_AAPL),
              97_1000L, 100L, List.of("MKAX")),
          new DemoInstrument(
              core("BBG00DEMOC31", "Volkswagen 4.125% 2031", InstrumentType.CORPORATE_SENIOR,
                  CurrencyCode.EUR, "DE", SettlementConvention.T_PLUS_2, LEI_VW),
              99_4000L, 100L, List.of("MAEL")),
          new DemoInstrument(
              core("BBG00DEMOCV0", "Microsoft 0% 2030 Convertible", InstrumentType.CONVERTIBLE,
                  CurrencyCode.USD, "US", SettlementConvention.T_PLUS_1, LEI_MSFT),
              112_5000L, 100L, List.of("MKAX")),
          // Samurai (18.30): JPY-denominated debt issued in Japan by a FOREIGN (US) issuer —
          // denomination is independent of the issuer; groups under Microsoft with the USD line.
          new DemoInstrument(
              core("BBG00DEMOSM1", "Microsoft 0.8% 2031 Samurai", InstrumentType.CORPORATE_SENIOR,
                  CurrencyCode.JPY, "JP", SettlementConvention.T_PLUS_2, LEI_MSFT),
              100_2000L, 100L, List.of("TWJP")),
          // ── FX (spot + forward — no single issuer) ─────────────────────────────
          new DemoInstrument(
              core("BBG00DEMOFX1", "EUR/USD Spot", InstrumentType.FX_SPOT,
                  CurrencyCode.USD, "US", SettlementConvention.T_PLUS_2, null),
              1_0842L, 100_000L, List.of("FXAL", "EBSX")),
          new DemoInstrument(
              core("BBG00DEMOFX2", "USD/JPY 1M Forward", InstrumentType.FX_FORWARD,
                  CurrencyCode.JPY, "JP", SettlementConvention.T_PLUS_2_AFTER_FIXING, null),
              156_2500L, 100_000L, List.of("FXAL")),
          // ── Listed derivatives (single-name options take the UNDERLYING's issuer;
          //    index futures have no single issuer) ─────────────────────────────────
          new DemoInstrument(
              core("BBG00DEMOES6", "E-mini S&P 500 Sep26", InstrumentType.LISTED_FUTURE,
                  CurrencyCode.USD, "US", SettlementConvention.PER_CCP, null),
              5300_2500L, 1L, List.of("XCME")),
          new DemoInstrument(
              core("BBG00DEMOSX6", "EURO STOXX 50 Sep26", InstrumentType.LISTED_FUTURE,
                  CurrencyCode.EUR, "DE", SettlementConvention.PER_CCP, null),
              4925_0000L, 1L, List.of("XEUR")),
          new DemoInstrument(
              core("BBG00DEMOMO6", "MSFT Sep26 450 Call", InstrumentType.LISTED_OPTION,
                  CurrencyCode.USD, "US", SettlementConvention.PER_CCP, LEI_MSFT),
              12_6000L, 1L, List.of("XCBO")),
          // ── Rates (IRS — no single issuer) ─────────────────────────────────────
          new DemoInstrument(
              core("BBG00DEMOIR5", "USD SOFR IRS 5Y", InstrumentType.VANILLA_IRS,
                  CurrencyCode.USD, "US", SettlementConvention.PER_CCP, null),
              4_0250L, 1_000_000L, List.of("TWSD")),
          new DemoInstrument(
              core("BBG00DEMOIR6", "EUR ESTR IRS 5Y", InstrumentType.VANILLA_IRS,
                  CurrencyCode.EUR, "DE", SettlementConvention.PER_CCP, null),
              2_6150L, 1_000_000L, List.of("BGCD")));

  /**
   * Per-figi currency-profile overrides (18.30): FX pairs carry BASE/QUOTE (price is
   * quote-per-base, qty is base notional); Shell quotes in GBp pence on XLON (minor unit).
   * Everything else collapses via {@link CurrencyProfile#defaults}.
   */
  private static final Map<String, CurrencyProfile> PROFILE_OVERRIDES =
      Map.of(
          "BBG00DEMOFX1", CurrencyProfile.fxPair(CurrencyCode.EUR, CurrencyCode.USD),
          "BBG00DEMOFX2", CurrencyProfile.fxPair(CurrencyCode.USD, CurrencyCode.JPY),
          "BBG00DEMOSHL",
              new CurrencyProfile(CurrencyCode.GBP, true, CurrencyCode.GBP, null, null));

  /** Resolve an instrument's currency roles — explicit override or the collapsed default. */
  public static CurrencyProfile profileOf(InstrumentCore core) {
    CurrencyProfile override = PROFILE_OVERRIDES.get(core.figi());
    return override != null ? override : CurrencyProfile.defaults(core);
  }

  /**
   * figi → P&L conversion key: the TRADING currency, with minor-unit lines keyed separately
   * ("GBX" = pence) so a 2,650.5 GBp mark converts at rate/100 instead of 100× off.
   */
  public static final Map<String, String> CURRENCY_OF =
      INSTRUMENTS.stream()
          .collect(Collectors.toUnmodifiableMap(
              DemoInstrument::figi,
              i -> {
                CurrencyProfile p = profileOf(i.core());
                if (p.tradingMinorUnit() && p.tradingCurrency() == CurrencyCode.GBP) {
                  return "GBX";
                }
                return p.tradingCurrency().name();
              }));

  /** Demo FX rates: USD per 1 unit of currency, fixed-point 4dp (PnlService.setFxRate). */
  public static final Map<String, Long> FX_TO_USD =
      Map.of("EUR", 1_0842L, "GBP", 1_2710L, "JPY", 64L, "GBX", 127L);

  private DemoUniverse() {}

  private static InstrumentCore core(
      String figi,
      String name,
      InstrumentType type,
      CurrencyCode currency,
      String country,
      SettlementConvention settlement,
      String issuerLei) {
    return new InstrumentCore(
        figi,
        "IID-" + figi,
        null,
        null,
        type.assetClass,
        type,
        name,
        name,
        issuerLei,
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
