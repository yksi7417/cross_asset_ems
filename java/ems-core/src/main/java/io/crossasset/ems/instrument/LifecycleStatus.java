/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import java.util.Optional;

/**
 * Trading lifecycle status of an instrument, as defined in arch-security-master.
 *
 * <p>Names follow the arch doc (ACTIVE, SUSPENDED, EXPIRED, MATURED, DEFAULTED). The deployed SBE
 * XML schemas use different names for wire codes 2-4 (SUPERSEDED, RETIRED, DRAFT — editorial
 * workflow states). The name-to-code mapping will be reconciled in task 4.11.
 */
public enum LifecycleStatus {
  UNKNOWN(0),
  ACTIVE(1),
  SUSPENDED(2),
  EXPIRED(3),
  MATURED(4),
  DEFAULTED(5);

  public final int wireCode;

  LifecycleStatus(int wireCode) {
    this.wireCode = wireCode;
  }

  public static Optional<LifecycleStatus> fromWire(int code) {
    for (LifecycleStatus v : values()) {
      if (v.wireCode == code) return Optional.of(v);
    }
    return Optional.empty();
  }
}
