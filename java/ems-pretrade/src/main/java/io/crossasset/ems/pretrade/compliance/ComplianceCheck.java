/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.compliance;

import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * One pluggable pre-trade compliance rule (task 10.1). Checks are authored by compliance policy,
 * not engineering releases: the gate composes whatever set is registered. Returning empty means
 * ALLOW from this rule; a {@link Finding} raises a BLOCK (with its override path) or a WARN.
 *
 * <p>Concrete rules land as their tasks: machine-gun rate/aggregation (10.3), allow / restricted /
 * watch lists (10.4), fat-finger (10.2, waiting on the 9.5 reference-price deferral), position /
 * risk checks (10.6/10.7).
 */
public interface ComplianceCheck {

  /** Stable rule identifier for audit linkage (e.g. {@code machine-gun-count}). */
  String ruleId();

  /** Evaluate one operation; empty = this rule allows it. */
  Optional<Finding> evaluate(ComplianceOperation operation);

  /** A non-ALLOW outcome from one rule. {@code overridePath} is required when blocking. */
  record Finding(ComplianceOutcome outcome, String rationale, @Nullable OverridePath overridePath) {
    public Finding {
      Objects.requireNonNull(outcome, "outcome");
      Objects.requireNonNull(rationale, "rationale");
      if (outcome == ComplianceOutcome.BLOCK && overridePath == null) {
        throw new IllegalArgumentException("BLOCK findings must carry an override path");
      }
      if (outcome == ComplianceOutcome.ALLOW) {
        throw new IllegalArgumentException("ALLOW is expressed by returning empty");
      }
    }
  }
}
