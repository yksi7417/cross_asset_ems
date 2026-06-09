/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import java.util.Optional;

/**
 * Types of corporate actions that affect positions, prices, and reference data.
 *
 * <p>Task 4.20 — Corporate actions → supersession integration.
 */
public enum CorporateActionType {
  CASH_DIVIDEND(1),
  STOCK_SPLIT(2),
  REVERSE_SPLIT(3),
  STOCK_DIVIDEND(4),
  SPIN_OFF(5),
  MERGER_ACQUISITION(6),
  NAME_CHANGE(7),
  RIGHTS_ISSUE(8),
  TENDER_OFFER(9),
  REDEMPTION(10),
  CONVERSION(11),
  COUPON(12),
  REORGANIZATION(13);

  public final int wireCode;

  CorporateActionType(int wireCode) {
    this.wireCode = wireCode;
  }

  public static Optional<CorporateActionType> fromWire(int code) {
    for (CorporateActionType v : values()) {
      if (v.wireCode == code) return Optional.of(v);
    }
    return Optional.empty();
  }
}
