/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory;

import java.util.Map;
import java.util.Objects;

/**
 * A trade presented for regulatory reporting. {@code assetClass} + {@code jurisdiction} drive the
 * determination matrix; {@code fields} carries the per-regulator data points (e.g. TRACE party id,
 * yield, trade modifiers) so the required-field validator can check completeness without a typed
 * model per regulator.
 */
public record ReportableTrade(
    String tradeRef,
    String assetClass,
    String jurisdiction,
    String instrumentId,
    int side,
    long qty,
    long price,
    Map<String, String> fields) {

  public ReportableTrade {
    Objects.requireNonNull(tradeRef, "tradeRef");
    Objects.requireNonNull(assetClass, "assetClass");
    Objects.requireNonNull(jurisdiction, "jurisdiction");
    fields = fields == null ? Map.of() : Map.copyOf(fields);
  }

  /** Whether a required field is present and non-blank. */
  public boolean hasField(String key) {
    String v = fields.get(key);
    return v != null && !v.isBlank();
  }
}
