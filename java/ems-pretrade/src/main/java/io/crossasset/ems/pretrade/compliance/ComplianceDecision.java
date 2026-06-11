/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.compliance;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The gate's verdict on one operation (arch-compliance § Decision shape). {@code ruleResults}
 * records every registered rule's outcome (including ALLOWs) so audit can reconstruct the whole
 * evaluation; {@code blockId} is set when the outcome is BLOCK and tracks the pending override.
 */
public record ComplianceDecision(
    ComplianceOutcome outcome,
    String checkId,
    @Nullable String blockId,
    List<RuleResult> ruleResults) {

  public ComplianceDecision {
    Objects.requireNonNull(outcome, "outcome");
    Objects.requireNonNull(checkId, "checkId");
    ruleResults = List.copyOf(Objects.requireNonNull(ruleResults, "ruleResults"));
  }

  /** One rule's contribution to the decision. */
  public record RuleResult(String ruleId, ComplianceOutcome outcome, String rationale) {}
}
