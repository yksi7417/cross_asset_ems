/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.refdata;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * A named collection of tick-size rules for a price band.
 *
 * <p>{@link #regimeRef} matches {@code InstrumentCore.tickSizeRegimeRef()}, linking the instrument
 * to its market microstructure rules.
 *
 * <p>Task 4.21 — Reference data service.
 */
public record TickSizeRegime(int regimeRef, String description, List<TickSizeEntry> entries) {

  public TickSizeRegime {
    if (entries == null || entries.isEmpty())
      throw new IllegalArgumentException("entries must not be null or empty");
    entries = List.copyOf(entries);
  }

  /**
   * Returns the tick size for the given price, or empty if no rule matches.
   *
   * <p>Rules are checked in definition order; the first matching band wins.
   */
  public Optional<BigDecimal> lookupTickSize(BigDecimal price) {
    for (TickSizeEntry entry : entries) {
      if (entry.contains(price)) return Optional.of(entry.tickSize());
    }
    return Optional.empty();
  }

  /** Returns the lot size for the given price, or empty if no rule matches. */
  public Optional<Integer> lookupLotSize(BigDecimal price) {
    for (TickSizeEntry entry : entries) {
      if (entry.contains(price)) return Optional.of(entry.lotSize());
    }
    return Optional.empty();
  }
}
