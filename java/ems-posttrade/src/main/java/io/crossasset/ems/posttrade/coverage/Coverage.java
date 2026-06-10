/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.coverage;

import io.crossasset.ems.instrument.AssetClass;

/**
 * The asset-class coverage labels the post-trade pipeline supports (Phase 16). Finer-grained than
 * the canonical {@link AssetClass} (e.g. common vs preferred equity, spot vs forward FX, corp vs
 * treasury fixed income both fold to one {@code AssetClass}) because their post-trade handling —
 * stages, confirmation tolerance, regulator, lot size — differs. Each label carries its parent
 * {@code AssetClass} for the instrument/SBE side.
 */
public enum Coverage {
  US_IG_CORP(AssetClass.FIXED_INCOME),
  TREASURY(AssetClass.FIXED_INCOME),
  US_EQUITY(AssetClass.EQUITY),
  PREFERRED(AssetClass.EQUITY),
  LISTED_FUT_OPT(AssetClass.LISTED_DERIVATIVE),
  FX_SPOT(AssetClass.FX),
  FX_FORWARD(AssetClass.FX);

  private final AssetClass assetClass;

  Coverage(AssetClass assetClass) {
    this.assetClass = assetClass;
  }

  /** The canonical instrument asset class this coverage label belongs to. */
  public AssetClass assetClass() {
    return assetClass;
  }
}
