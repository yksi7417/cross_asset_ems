/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.crossasset.ems.instrument.AssetClass;
import io.crossasset.ems.instrument.InstrumentType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Pins the 18.21 demo-universe coverage rule: every supported asset class ships at least two
 * instruments — at least one US and at least one international — with sane per-class trading
 * conventions, so the desktop demo is genuinely cross-asset.
 */
class DemoUniverseTest {

  private static final Set<AssetClass> SUPPORTED =
      Set.of(
          AssetClass.EQUITY,
          AssetClass.FIXED_INCOME,
          AssetClass.FX,
          AssetClass.LISTED_DERIVATIVE,
          AssetClass.RATES_DERIVATIVE);

  @Test
  void everySupportedAssetClassHasTwoInstrumentsUsAndInternational() {
    Map<AssetClass, List<DemoUniverse.DemoInstrument>> byClass =
        DemoUniverse.INSTRUMENTS.stream()
            .collect(Collectors.groupingBy(DemoUniverse.DemoInstrument::assetClass));
    assertEquals(SUPPORTED, byClass.keySet(), "universe covers exactly the supported classes");
    for (AssetClass assetClass : SUPPORTED) {
      List<DemoUniverse.DemoInstrument> instruments = byClass.get(assetClass);
      assertTrue(instruments.size() >= 2, assetClass + " needs >= 2 instruments");
      assertTrue(
          instruments.stream().anyMatch(i -> "US".equals(i.core().countryOfIssue())),
          assetClass + " needs a US instrument");
      assertTrue(
          instruments.stream().anyMatch(i -> !"US".equals(i.core().countryOfIssue())),
          assetClass + " needs an international instrument");
    }
  }

  @Test
  void fixedIncomeCoversBothGovernmentAndCorporateCredit() {
    Set<InstrumentType> fiTypes =
        DemoUniverse.INSTRUMENTS.stream()
            .map(i -> i.core().instrumentType())
            .filter(t -> t.assetClass == AssetClass.FIXED_INCOME)
            .collect(Collectors.toSet());
    assertTrue(fiTypes.contains(InstrumentType.TREASURY), "government bonds present");
    assertTrue(fiTypes.contains(InstrumentType.CORPORATE_SENIOR), "corporate credit present");
  }

  @Test
  void fxCoversSpotAndForward() {
    Set<InstrumentType> fxTypes =
        DemoUniverse.INSTRUMENTS.stream()
            .map(i -> i.core().instrumentType())
            .filter(t -> t.assetClass == AssetClass.FX)
            .collect(Collectors.toSet());
    assertTrue(fxTypes.contains(InstrumentType.FX_SPOT), "FX spot present");
    assertTrue(fxTypes.contains(InstrumentType.FX_FORWARD), "FX forward present");
  }

  @Test
  void identifiersAndConventionsAreSane() {
    Set<String> figis =
        DemoUniverse.INSTRUMENTS.stream()
            .map(DemoUniverse.DemoInstrument::figi)
            .collect(Collectors.toSet());
    assertEquals(DemoUniverse.INSTRUMENTS.size(), figis.size(), "FIGIs are unique");
    for (DemoUniverse.DemoInstrument inst : DemoUniverse.INSTRUMENTS) {
      assertTrue(inst.basePx() > 0, inst.figi() + " base price positive");
      assertTrue(inst.lotQty() > 0, inst.figi() + " lot positive");
      assertFalse(inst.venues().isEmpty(), inst.figi() + " has at least one venue");
      assertTrue(
          DemoUniverse.CURRENCY_OF.containsKey(inst.figi()), inst.figi() + " has a currency");
    }
  }

  @Test
  void issuerGroupingCollapsesACapitalStructure() {
    // 18.29: Microsoft's stock, convertible and listed option share one issuer node.
    List<DemoUniverse.DemoInstrument> msft =
        DemoUniverse.INSTRUMENTS.stream()
            .filter(i -> "LEI-DEMO-MSFT".equals(i.core().issuerLei()))
            .toList();
    Set<InstrumentType> msftTypes =
        msft.stream().map(i -> i.core().instrumentType()).collect(Collectors.toSet());
    assertTrue(
        msftTypes.containsAll(
            Set.of(
                InstrumentType.COMMON_STOCK,
                InstrumentType.CONVERTIBLE,
                InstrumentType.LISTED_OPTION)),
        "MSFT issuer groups stock + convertible + option, got " + msftTypes);

    for (DemoUniverse.DemoInstrument inst : DemoUniverse.INSTRUMENTS) {
      String lei = inst.core().issuerLei();
      InstrumentType type = inst.core().instrumentType();
      if (type == InstrumentType.FX_SPOT
          || type == InstrumentType.FX_FORWARD
          || type == InstrumentType.VANILLA_IRS) {
        assertTrue(lei == null, inst.figi() + " has no single issuer — must carry none");
      }
      if (lei != null) {
        assertTrue(
            DemoUniverse.ISSUER_NAMES.containsKey(lei),
            lei + " must resolve in the demo issuer directory");
      }
    }
  }

  @Test
  void everyNonUsdCurrencyHasAnFxRateForPnlConversion() {
    Set<String> nonUsd =
        DemoUniverse.CURRENCY_OF.values().stream()
            .filter(c -> !"USD".equals(c))
            .collect(Collectors.toSet());
    for (String currency : nonUsd) {
      assertTrue(
          DemoUniverse.FX_TO_USD.containsKey(currency),
          currency + " needs an FX rate or international P&L silently drops to zero");
    }
  }

  // ── Currency roles (18.30) — one pin per security type from [[currency-in-execution]] ──

  private static DemoUniverse.DemoInstrument byFigi(String figi) {
    return DemoUniverse.INSTRUMENTS.stream()
        .filter(i -> i.figi().equals(figi))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void fxPairsCarryBaseAndQuote_priceIsQuotePerBase() {
    var eurusd = DemoUniverse.profileOf(byFigi("BBG00DEMOFX1").core());
    assertEquals("EUR", eurusd.baseCurrency().name(), "EUR/USD: base is the unit bought/sold");
    assertEquals("USD", eurusd.quoteCurrency().name(), "EUR/USD: price denominates in USD");
    assertEquals("USD", eurusd.tradingCurrency().name());

    var usdjpy = DemoUniverse.profileOf(byFigi("BBG00DEMOFX2").core());
    assertEquals("USD", usdjpy.baseCurrency().name(), "USD/JPY is NOT JPY/USD — convention");
    assertEquals("JPY", usdjpy.quoteCurrency().name());
  }

  @Test
  void samuraiBond_denominationIsIndependentOfIssuer() {
    var samurai = byFigi("BBG00DEMOSM1");
    assertEquals("JPY", samurai.core().currency().name(), "JPY-denominated");
    assertEquals("JP", samurai.core().countryOfIssue(), "issued in Japan");
    assertEquals("LEI-DEMO-MSFT", samurai.core().issuerLei(), "by a US issuer");
    var profile = DemoUniverse.profileOf(samurai.core());
    assertEquals("JPY", profile.tradingCurrency().name());
    assertEquals("JPY", profile.settlementCurrency().name());
    assertFalse(profile.isFxPair());
  }

  @Test
  void adr_tradesUsdWhileItsUnderlyingLineTradesJpy_sameIssuer() {
    var adr = byFigi("BBG00DEMOADR");
    var local = byFigi("BBG000BCM915");
    assertEquals("USD", DemoUniverse.profileOf(adr.core()).tradingCurrency().name());
    assertEquals("JPY", DemoUniverse.profileOf(local.core()).tradingCurrency().name());
    assertEquals(local.core().issuerLei(), adr.core().issuerLei(), "one issuer, two wrappers");
  }

  @Test
  void minorUnitListing_isFlaggedAndPnlConvertsAtPenceRate() {
    var shell = DemoUniverse.profileOf(byFigi("BBG00DEMOSHL").core());
    assertTrue(shell.tradingMinorUnit(), "Shell quotes in GBp pence on XLON");
    assertEquals("GBP", shell.tradingCurrency().name());
    // P&L keys pence separately: GBX rate = GBP rate / 100, or marks convert 100x off.
    assertEquals("GBX", DemoUniverse.CURRENCY_OF.get("BBG00DEMOSHL"));
    assertEquals(
        DemoUniverse.FX_TO_USD.get("GBP") / 100, (long) DemoUniverse.FX_TO_USD.get("GBX"));
  }

  @Test
  void collapsedDefaults_everyOtherInstrumentTradesAndSettlesItsCoreCurrency() {
    for (DemoUniverse.DemoInstrument inst : DemoUniverse.INSTRUMENTS) {
      var profile = DemoUniverse.profileOf(inst.core());
      if (!profile.isFxPair() && !profile.tradingMinorUnit()) {
        assertEquals(inst.core().currency(), profile.tradingCurrency(), inst.figi());
        assertEquals(inst.core().currency(), profile.settlementCurrency(), inst.figi());
      }
    }
  }
}
