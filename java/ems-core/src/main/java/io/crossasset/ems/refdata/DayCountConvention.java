/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.refdata;

import java.util.List;

/**
 * Day count convention definition.
 *
 * <p>Encodes how time fractions are computed for accrual calculations (bonds, swaps, money market).
 * FpML and FIXML identifiers allow round-trip from external messages.
 *
 * <p>Task 4.21 — Reference data service.
 */
public record DayCountConvention(
    String code,
    String displayName,
    int fpmlId,
    String fixmlName,
    String description,
    String yearFractionFormula,
    List<String> useCases) {

  public DayCountConvention {
    if (code == null || code.isBlank()) throw new IllegalArgumentException("code required");
    useCases = useCases != null ? List.copyOf(useCases) : List.of();
  }
}
