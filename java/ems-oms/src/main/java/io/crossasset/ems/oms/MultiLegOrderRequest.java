/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Staging input for a multi-leg / package order. Per arch-multileg.md.
 *
 * <p>{@code packageId} is the venue-side package identifier — required for ALL_OR_NONE
 * (EMS-ORD-4002). {@code sequencePolicy} ("spot_first" or "declared_order") is only valid with
 * {@link ExecutionMode#SEQUENCED} (EMS-ORD-4003); a null policy under SEQUENCED means declared
 * (list) order. {@code tif} uses the FIX tag 59 integer code.
 */
public record MultiLegOrderRequest(
    String requestId,
    long sessionId,
    String clOrdId,
    MultiLegKind kind,
    ExecutionMode executionMode,
    List<LegRequest> legs,
    @Nullable String packageId,
    @Nullable String sequencePolicy,
    String account,
    int tif) {
  public MultiLegOrderRequest {
    Objects.requireNonNull(requestId, "requestId");
    Objects.requireNonNull(clOrdId, "clOrdId");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(executionMode, "executionMode");
    Objects.requireNonNull(account, "account");
    legs = List.copyOf(Objects.requireNonNull(legs, "legs"));
  }
}
