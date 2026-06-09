/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import java.util.Optional;

/**
 * Order side carried in each {@link Leg}, aligned with FIX Protocol tag 54 wire values.
 *
 * <p>BUY=1 and SELL=2 are universal. SELL_SHORT=5 follows FIX 4.4+ convention.
 */
public enum Side {
  BUY(1),
  SELL(2),
  BUY_MINUS(3),
  SELL_PLUS(4),
  SELL_SHORT(5);

  public final int wireCode;

  Side(int wireCode) {
    this.wireCode = wireCode;
  }

  public static Optional<Side> fromWire(int code) {
    for (Side v : values()) {
      if (v.wireCode == code) return Optional.of(v);
    }
    return Optional.empty();
  }
}
