/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import java.util.Optional;

/**
 * Fungibility of an instrument, as encoded in the InstrumentCore SBE block.
 *
 * <p>Wire ordinals are consistent across equity-instrument.xml and bond-instrument.xml: FUNGIBLE=1,
 * NON_FUNGIBLE=2, TBA_LIKE=3.
 */
public enum Fungibility {
  UNKNOWN(0),
  FUNGIBLE(1),
  NON_FUNGIBLE(2),
  TBA_LIKE(3);

  public final int wireCode;

  Fungibility(int wireCode) {
    this.wireCode = wireCode;
  }

  public static Optional<Fungibility> fromWire(int code) {
    for (Fungibility v : values()) {
      if (v.wireCode == code) return Optional.of(v);
    }
    return Optional.empty();
  }
}
