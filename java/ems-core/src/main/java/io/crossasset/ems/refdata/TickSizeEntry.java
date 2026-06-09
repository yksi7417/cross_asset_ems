/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.refdata;

import java.math.BigDecimal;

/**
 * A single price-range / tick-size rule within a tick size regime.
 *
 * <p>{@code priceRangeHigh} is {@code null} for the open-ended top tier. The range is inclusive of
 * {@code priceRangeLow} and exclusive of {@code priceRangeHigh}.
 *
 * <p>Task 4.21 — Reference data service.
 */
public record TickSizeEntry(
    BigDecimal priceRangeLow, BigDecimal priceRangeHigh, BigDecimal tickSize, int lotSize) {

  public TickSizeEntry {
    if (priceRangeLow == null) throw new IllegalArgumentException("priceRangeLow required");
    if (tickSize == null || tickSize.compareTo(BigDecimal.ZERO) <= 0)
      throw new IllegalArgumentException("tickSize must be positive");
    if (lotSize <= 0) throw new IllegalArgumentException("lotSize must be positive");
  }

  public boolean contains(BigDecimal price) {
    if (price.compareTo(priceRangeLow) < 0) return false;
    return priceRangeHigh == null || price.compareTo(priceRangeHigh) < 0;
  }
}
