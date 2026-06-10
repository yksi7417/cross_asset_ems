/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import java.util.List;

/**
 * Pure rule-evaluation engine for OMS automation.
 *
 * <p>The engine is side-effect-free: it evaluates rules and returns {@link RuleFiringDecision}
 * descriptors. It is the caller's responsibility to execute any resulting {@link AutomationAction}
 * against the appropriate manager (e.g. {@link RouteManager}, {@link StagedOrderManager}).
 *
 * <p>Rules are bound at runtime and evaluated in descending {@link AutomationRule#priority} order.
 */
public interface AutomationEngine {

  /** Add or replace a rule. Replaces any existing rule with the same {@code ruleId}. */
  void bindRule(AutomationRule rule);

  /** Remove the rule with the given {@code ruleId}. No-op if not found. */
  void unbindRule(String ruleId);

  /**
   * Evaluate all bound rules against {@code event} and {@code context}.
   *
   * <p>Returns one {@link RuleFiringDecision} per candidate rule in priority order. Only rules
   * whose {@code triggerEvent} matches the event name and whose {@code condition} returns {@code
   * true} produce a {@link RuleFiringDecision.Fired}; all others produce {@link
   * RuleFiringDecision.Skipped}.
   */
  List<RuleFiringDecision> evaluate(AutomationEvent event, AutomationContext context);

  /** Return all currently bound rules sorted by descending priority. */
  List<AutomationRule> listRules();
}
