/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import java.util.Optional;

/**
 * Global instrument asset class carried in every {@link InstrumentCore} block.
 *
 * <p>Wire codes are sourced from the SBE instrument XML schemas where they are explicitly defined:
 * EQUITY=2 (equity-instrument.xml), FIXED_INCOME=5 (bond-instrument.xml), FX=10
 * (fx-spot-instrument.xml). Codes for other classes are provisional; they will be reconciled when
 * task 4.11 brings all instrument schemas to a unified namespace.
 */
public enum AssetClass {
  UNKNOWN(0),
  LISTED_DERIVATIVE(1),
  EQUITY(2),
  RATES_DERIVATIVE(3),
  CREDIT_DERIVATIVE(4),
  FIXED_INCOME(5),
  COMMODITY(6),
  COMMODITY_PHYSICAL(7),
  CRYPTO(8),
  FX(10);

  public final int wireCode;

  AssetClass(int wireCode) {
    this.wireCode = wireCode;
  }

  public static Optional<AssetClass> fromWire(int code) {
    for (AssetClass v : values()) {
      if (v.wireCode == code) return Optional.of(v);
    }
    return Optional.empty();
  }
}
