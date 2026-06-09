/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import java.util.Optional;

/**
 * Classification of an {@link InstrumentPackage}, as defined in arch-security-master.
 *
 * <p>Wire ordinals are canonical; no SBE XML schema exists for Package yet (pending task 4.11
 * namespace reconciliation).
 */
public enum PackageType {
  SINGLE(1),
  MULTI_LEG(2),
  PAIRED(3),
  BWIC_LIST(4),
  ETF_RFQ_BLOCK(5);

  public final int wireCode;

  PackageType(int wireCode) {
    this.wireCode = wireCode;
  }

  public static Optional<PackageType> fromWire(int code) {
    for (PackageType v : values()) {
      if (v.wireCode == code) return Optional.of(v);
    }
    return Optional.empty();
  }
}
