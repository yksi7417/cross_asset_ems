/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import java.util.Optional;

/**
 * Settlement convention encoded in the InstrumentCore SBE block.
 *
 * <p>Wire ordinals are consistent across equity-instrument.xml (T_PLUS_0..T_PLUS_3) and
 * bond-instrument.xml (adds TBA_MONTHLY=5, PER_CCP=6). T_PLUS_2_AFTER_FIXING=7 is added per
 * arch-security-master for FX NDF instruments.
 */
public enum SettlementConvention {
  UNKNOWN(0),
  T_PLUS_0(1),
  T_PLUS_1(2),
  T_PLUS_2(3),
  T_PLUS_3(4),
  TBA_MONTHLY(5),
  PER_CCP(6),
  T_PLUS_2_AFTER_FIXING(7);

  public final int wireCode;

  SettlementConvention(int wireCode) {
    this.wireCode = wireCode;
  }

  public static Optional<SettlementConvention> fromWire(int code) {
    for (SettlementConvention v : values()) {
      if (v.wireCode == code) return Optional.of(v);
    }
    return Optional.empty();
  }
}
