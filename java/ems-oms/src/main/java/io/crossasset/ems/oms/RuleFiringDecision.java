/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import java.util.List;

/**
 * Decision produced for each rule evaluated by {@link AutomationEngine#evaluate}.
 *
 * <p>Every rule touched during evaluation produces exactly one decision. Callers can inspect {@link
 * Fired} results to discover which actions need to be applied.
 */
public sealed interface RuleFiringDecision
    permits RuleFiringDecision.Fired, RuleFiringDecision.Skipped {

  String ruleId();

  /** The rule matched — execute its actions. */
  record Fired(String ruleId, List<AutomationAction> actions) implements RuleFiringDecision {}

  /** The rule did not match (disabled, wrong event, condition false). */
  record Skipped(String ruleId, String reason) implements RuleFiringDecision {}
}
