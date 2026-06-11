/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.compliance;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One suspended operation awaiting compliance review (arch-compliance: the operation enters {@code
 * PendingCompliance}; FIX-paired clients see OrdStatus=9 Suspended). Immutable view — resolution
 * state transitions happen in the {@link ComplianceGate}'s block store, and the tag-gated,
 * time-bound release/deny mechanics are task 10.5.
 */
public record PendingBlock(
    String blockId,
    String checkId,
    ComplianceOperation operation,
    String ruleId,
    String rationale,
    OverridePath overridePath,
    Status status,
    @Nullable String resolvedBy,
    @Nullable String resolutionRationale) {

  public enum Status {
    PENDING,
    RELEASED,
    DENIED
  }

  public PendingBlock {
    Objects.requireNonNull(blockId, "blockId");
    Objects.requireNonNull(checkId, "checkId");
    Objects.requireNonNull(operation, "operation");
    Objects.requireNonNull(ruleId, "ruleId");
    Objects.requireNonNull(rationale, "rationale");
    Objects.requireNonNull(overridePath, "overridePath");
    Objects.requireNonNull(status, "status");
  }
}
