/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.confirmation;

import java.util.List;

/**
 * Outcome of comparing two {@link TradeRecord}s. When {@code matched} is false, {@code
 * differingFields} names the fields that fell outside tolerance, for the dispute queue.
 */
public record MatchResult(boolean matched, List<String> differingFields) {

  public MatchResult {
    differingFields = List.copyOf(differingFields);
  }

  public static MatchResult ofMatch() {
    return new MatchResult(true, List.of());
  }

  public static MatchResult ofMismatch(List<String> fields) {
    return new MatchResult(false, fields);
  }
}
