/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Static registry of all SBE instrument message templates that have a deployed schema file.
 *
 * <p>Registry entries are sourced from the SBE XML files in {@code schemas/sbe/} and verified by
 * {@code InstrumentTemplateRegistryTest#eachRegisteredTemplate_matchesItsSchemaFile}. Templates
 * whose schema files do not yet exist (TBA-MBS, SpecifiedPool, ETF, StructuredProduct,
 * EventContract) are intentionally absent — they are added by their respective tasks (4.12, 4.14,
 * 4.17) when the schema lands.
 *
 * <p>Task 4.3 — SBE template registry for Instrument templates.
 */
public final class InstrumentTemplateRegistry {

  public static final InstrumentTemplateRegistry INSTANCE = new InstrumentTemplateRegistry();

  private final Map<Integer, InstrumentTemplateDescriptor> byTemplateId;

  private InstrumentTemplateRegistry() {
    List<InstrumentTemplateDescriptor> templates =
        List.of(
            // Equity
            desc(0x2001, "EquityInstrument", InstrumentAssetClass.EQUITY, "equity-instrument.xml"),
            // Fixed Income
            desc(
                0x2002, "BondInstrument", InstrumentAssetClass.FIXED_INCOME, "bond-instrument.xml"),
            desc(
                0x2003,
                "ConvertibleBondInstrument",
                InstrumentAssetClass.FIXED_INCOME,
                "convertible-bond-instrument.xml"),
            desc(
                0x2004, "LoanInstrument", InstrumentAssetClass.FIXED_INCOME, "loan-instrument.xml"),
            desc(0x2012, "AbsInstrument", InstrumentAssetClass.FIXED_INCOME, "abs-instrument.xml"),
            // Listed Derivatives
            desc(
                0x2030,
                "ListedOptionInstrument",
                InstrumentAssetClass.LISTED_DERIVATIVE,
                "listed-option-instrument.xml"),
            desc(
                0x2031,
                "ListedFutureInstrument",
                InstrumentAssetClass.LISTED_DERIVATIVE,
                "listed-future-instrument.xml"),
            // FX
            desc(0x2040, "FxSpotInstrument", InstrumentAssetClass.FX, "fx-spot-instrument.xml"),
            desc(
                0x2041,
                "FxForwardInstrument",
                InstrumentAssetClass.FX,
                "fx-forward-instrument.xml"),
            desc(0x2042, "FxSwapInstrument", InstrumentAssetClass.FX, "fx-swap-instrument.xml"),
            desc(0x2043, "FxNdfInstrument", InstrumentAssetClass.FX, "fx-ndf-instrument.xml"),
            desc(0x2044, "FxOptionInstrument", InstrumentAssetClass.FX, "fx-option-instrument.xml"),
            // Rates Derivatives
            desc(
                0x2050,
                "IrsInstrument",
                InstrumentAssetClass.RATES_DERIVATIVE,
                "irs-instrument.xml"),
            // Credit Derivatives
            desc(
                0x2051,
                "CdsInstrument",
                InstrumentAssetClass.CREDIT_DERIVATIVE,
                "cds-instrument.xml"),
            // Commodities
            desc(
                0x2070,
                "CommodityFutureInstrument",
                InstrumentAssetClass.COMMODITY,
                "commodity-future-instrument.xml"),
            desc(
                0x2071,
                "CommodityPhysicalInstrument",
                InstrumentAssetClass.COMMODITY,
                "commodity-physical-instrument.xml"),
            // Crypto
            desc(
                0x2080,
                "CryptoFungibleInstrument",
                InstrumentAssetClass.CRYPTO,
                "crypto-fungible-instrument.xml"),
            desc(0x2081, "NftInstrument", InstrumentAssetClass.CRYPTO, "nft-instrument.xml"));

    Map<Integer, InstrumentTemplateDescriptor> map = new LinkedHashMap<>();
    for (InstrumentTemplateDescriptor t : templates) {
      map.put(t.templateId(), t);
    }
    byTemplateId = Collections.unmodifiableMap(map);
  }

  private static InstrumentTemplateDescriptor desc(
      int templateId, String name, InstrumentAssetClass assetClass, String filename) {
    return new InstrumentTemplateDescriptor(
        templateId, name, assetClass, "schemas/sbe/" + filename);
  }

  /** Returns the descriptor for {@code templateId}, or empty if not a registered template. */
  public Optional<InstrumentTemplateDescriptor> lookup(int templateId) {
    return Optional.ofNullable(byTemplateId.get(templateId));
  }

  /** Returns all registered templates for the given asset class. */
  public List<InstrumentTemplateDescriptor> forAssetClass(InstrumentAssetClass assetClass) {
    return byTemplateId.values().stream()
        .filter(t -> t.assetClass() == assetClass)
        .collect(Collectors.toList());
  }

  /** Returns all registered templates in insertion order. */
  public Collection<InstrumentTemplateDescriptor> all() {
    return byTemplateId.values();
  }

  /** Returns true if {@code templateId} is a registered instrument template. */
  public boolean isKnown(int templateId) {
    return byTemplateId.containsKey(templateId);
  }
}
