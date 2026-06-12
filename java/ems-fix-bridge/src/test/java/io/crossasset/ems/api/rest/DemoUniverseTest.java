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
}
