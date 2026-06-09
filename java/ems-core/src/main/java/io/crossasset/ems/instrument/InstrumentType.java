/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import java.util.Optional;

/**
 * Instrument sub-type within an {@link AssetClass}.
 *
 * <p>Wire codes are scoped per asset class — e.g. wireCode=1 means {@code COMMON_STOCK} in the
 * EQUITY context but {@code TREASURY} in the FIXED_INCOME context. Always interpret alongside the
 * {@code assetClass} field. Values are sourced from per-asset-class SBE instrument XML schemas.
 */
public enum InstrumentType {
  // Equity
  COMMON_STOCK(AssetClass.EQUITY, 1),
  PREFERRED(AssetClass.EQUITY, 2),
  ADR(AssetClass.EQUITY, 3),
  GDR(AssetClass.EQUITY, 4),
  ETF(AssetClass.EQUITY, 5),
  REIT(AssetClass.EQUITY, 6),
  RIGHT(AssetClass.EQUITY, 7),
  WARRANT(AssetClass.EQUITY, 8),
  UNIT(AssetClass.EQUITY, 9),
  // Fixed Income
  TREASURY(AssetClass.FIXED_INCOME, 1),
  CORPORATE_SENIOR(AssetClass.FIXED_INCOME, 2),
  CORPORATE_SUBORDINATED(AssetClass.FIXED_INCOME, 3),
  MUNICIPAL(AssetClass.FIXED_INCOME, 4),
  AGENCY(AssetClass.FIXED_INCOME, 5),
  SUPRANATIONAL(AssetClass.FIXED_INCOME, 6),
  COVERED(AssetClass.FIXED_INCOME, 7),
  CONVERTIBLE(AssetClass.FIXED_INCOME, 11),
  INDEX_LINKED(AssetClass.FIXED_INCOME, 12),
  ABS(AssetClass.FIXED_INCOME, 13),
  // Listed Derivatives
  LISTED_OPTION(AssetClass.LISTED_DERIVATIVE, 1),
  LISTED_FUTURE(AssetClass.LISTED_DERIVATIVE, 2),
  // FX
  FX_SPOT(AssetClass.FX, 1),
  FX_FORWARD(AssetClass.FX, 2),
  FX_SWAP(AssetClass.FX, 3),
  FX_NDF(AssetClass.FX, 4),
  FX_OPTION(AssetClass.FX, 5),
  // Rates Derivatives
  VANILLA_IRS(AssetClass.RATES_DERIVATIVE, 1),
  BASIS_SWAP(AssetClass.RATES_DERIVATIVE, 2),
  OIS(AssetClass.RATES_DERIVATIVE, 3),
  XCCY_SWAP(AssetClass.RATES_DERIVATIVE, 4),
  // Credit Derivatives
  CDS_SINGLE_NAME(AssetClass.CREDIT_DERIVATIVE, 1),
  CDS_INDEX(AssetClass.CREDIT_DERIVATIVE, 2),
  CDS_TRANCHE(AssetClass.CREDIT_DERIVATIVE, 3),
  // Commodity Derivatives
  COMMODITY_FUTURE(AssetClass.COMMODITY, 1),
  COMMODITY_OPTION(AssetClass.COMMODITY, 2),
  // Commodity Physical
  COMMODITY_PHYSICAL_SPOT(AssetClass.COMMODITY_PHYSICAL, 1),
  // Crypto
  CRYPTO_FUNGIBLE(AssetClass.CRYPTO, 1),
  NFT(AssetClass.CRYPTO, 2);

  public final AssetClass assetClass;
  public final int wireCode;

  InstrumentType(AssetClass assetClass, int wireCode) {
    this.assetClass = assetClass;
    this.wireCode = wireCode;
  }

  public static Optional<InstrumentType> fromWire(AssetClass ac, int code) {
    for (InstrumentType v : values()) {
      if (v.assetClass == ac && v.wireCode == code) return Optional.of(v);
    }
    return Optional.empty();
  }
}
