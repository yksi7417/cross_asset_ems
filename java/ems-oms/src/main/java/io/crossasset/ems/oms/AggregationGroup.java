/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Immutable view of an aggregation group. {@code aggOrderId} is the block parent's staged-order ID
 * — the group has no FSM of its own; its lifecycle mirrors the parent order (per the
 * arch-aggregation envelope). {@code avgPx} is the volume-weighted average price of all fills
 * allocated so far (0 before the first fill).
 */
public record AggregationGroup(
    String aggOrderId,
    List<String> childOrderIds,
    AllocationRule rule,
    @Nullable RoundingPolicy rounding,
    Map<String, Long> allocations,
    long totalAllocated,
    long avgPx) {
  public AggregationGroup {
    Objects.requireNonNull(aggOrderId, "aggOrderId");
    Objects.requireNonNull(rule, "rule");
    childOrderIds = List.copyOf(Objects.requireNonNull(childOrderIds, "childOrderIds"));
    allocations = Map.copyOf(Objects.requireNonNull(allocations, "allocations"));
  }

  /** Cumulative qty allocated to the given child so far. */
  public long allocatedQty(String childOrderId) {
    return allocations.getOrDefault(childOrderId, 0L);
  }
}
