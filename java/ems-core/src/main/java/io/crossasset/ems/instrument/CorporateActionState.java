/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import java.util.Optional;

/**
 * Lifecycle states of a corporate action.
 *
 * <p>State machine: ANNOUNCED → LOCKED (T-1 before ex_date) → APPLIED (ex_date) or CANCELLED.
 * ANNOUNCED may also transition directly to CANCELLED.
 *
 * <p>Task 4.20 — Corporate actions → supersession integration.
 */
public enum CorporateActionState {
  ANNOUNCED(1),
  LOCKED(2),
  APPLIED(3),
  CANCELLED(4);

  public final int wireCode;

  CorporateActionState(int wireCode) {
    this.wireCode = wireCode;
  }

  public static Optional<CorporateActionState> fromWire(int code) {
    for (CorporateActionState v : values()) {
      if (v.wireCode == code) return Optional.of(v);
    }
    return Optional.empty();
  }

  public boolean isTerminal() {
    return this == APPLIED || this == CANCELLED;
  }

  public boolean canTransitionTo(CorporateActionState next) {
    return switch (this) {
      case ANNOUNCED -> next == LOCKED || next == CANCELLED;
      case LOCKED -> next == APPLIED || next == CANCELLED;
      case APPLIED, CANCELLED -> false;
    };
  }
}
