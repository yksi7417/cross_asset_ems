/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Input for aggregating N staged orders into one block. {@code childOrderIds} order is significant
 * — it is the allocation order under {@link AllocationRule#SEQUENCED} and the residual order for
 * rounding. {@code rounding} is required for PRO_RATA / AVG_PRICE (EMS-ORD-5002). {@code account}
 * is the execution book the block parent is staged under; child accounts are untouched (post-trade
 * allocation per arch-allocation-service books to them).
 */
public record AggregationRequest(
    String requestId,
    long sessionId,
    String clOrdId,
    List<String> childOrderIds,
    AllocationRule rule,
    @Nullable RoundingPolicy rounding,
    String account) {
  public AggregationRequest {
    Objects.requireNonNull(requestId, "requestId");
    Objects.requireNonNull(clOrdId, "clOrdId");
    Objects.requireNonNull(rule, "rule");
    Objects.requireNonNull(account, "account");
    childOrderIds = List.copyOf(Objects.requireNonNull(childOrderIds, "childOrderIds"));
  }
}
