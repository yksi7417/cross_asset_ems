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
 * Immutable view of one netting group (one netting-key bucket that collapsed). {@code
 * parentOrderId} is the market-facing residual order — null when the bucket netted to zero (pure
 * internal cross; book via {@link NettingEngine#bookInternalCross}). {@code residualSide} uses FIX
 * tag 54 codes (0 when net-to-zero). {@code allocations} maps child order ID to cumulative booked
 * qty.
 */
public record NetGroup(
    String groupId,
    @Nullable String parentOrderId,
    String figi,
    String ccyPair,
    String valueDate,
    String accountGroup,
    @Nullable String pac,
    List<String> buyChildIds,
    List<String> sellChildIds,
    long buyQty,
    long sellQty,
    long residualQty,
    int residualSide,
    long matchedQty,
    long parentFilled,
    Map<String, Long> allocations) {
  public NetGroup {
    Objects.requireNonNull(groupId, "groupId");
    Objects.requireNonNull(figi, "figi");
    Objects.requireNonNull(ccyPair, "ccyPair");
    Objects.requireNonNull(valueDate, "valueDate");
    Objects.requireNonNull(accountGroup, "accountGroup");
    buyChildIds = List.copyOf(Objects.requireNonNull(buyChildIds, "buyChildIds"));
    sellChildIds = List.copyOf(Objects.requireNonNull(sellChildIds, "sellChildIds"));
    allocations = Map.copyOf(Objects.requireNonNull(allocations, "allocations"));
  }
}
