/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

/** Result of {@link AggregationManager#aggregate(AggregationRequest)}. */
public sealed interface AggregateResult
    permits AggregateResult.Aggregated, AggregateResult.Rejected {

  /** Block parent staged and READY; children frozen into the group. */
  record Aggregated(AggregationGroup group, StagedOrder parent) implements AggregateResult {}

  /** Aggregation refused; {@code rejectCode} is an EMS-ORD-* / EMS-PRM-* catalog code. */
  record Rejected(String requestId, String rejectCode, String message) implements AggregateResult {}
}
