/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

/** High-level asset class grouping used to classify SBE instrument templates. */
public enum InstrumentAssetClass {
  EQUITY,
  FIXED_INCOME,
  LISTED_DERIVATIVE,
  FX,
  RATES_DERIVATIVE,
  CREDIT_DERIVATIVE,
  COMMODITY,
  CRYPTO,
  STRUCTURED_PRODUCT,
  EVENT_CONTRACT
}
