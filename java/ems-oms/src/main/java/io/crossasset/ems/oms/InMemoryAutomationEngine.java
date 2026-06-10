/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory {@link AutomationEngine}.
 *
 * <p>Rules are stored in a {@link ConcurrentHashMap} keyed by {@code ruleId}. Each call to {@link
 * #evaluate} is a pure snapshot-read: it collects a sorted copy of matching rules at call time,
 * evaluates conditions, and returns decisions — it never mutates engine state.
 */
public final class InMemoryAutomationEngine implements AutomationEngine {

  private static final Comparator<AutomationRule> BY_PRIORITY_DESC =
      Comparator.comparingInt(AutomationRule::priority).reversed();

  private final ConcurrentHashMap<String, AutomationRule> rules = new ConcurrentHashMap<>();

  @Override
  public void bindRule(AutomationRule rule) {
    rules.put(rule.ruleId(), rule);
  }

  @Override
  public void unbindRule(String ruleId) {
    rules.remove(ruleId);
  }

  @Override
  public List<RuleFiringDecision> evaluate(AutomationEvent event, AutomationContext context) {
    return rules.values().stream()
        .filter(r -> r.triggerEvent().equals(event.eventName()))
        .sorted(BY_PRIORITY_DESC)
        .map(r -> decide(r, context))
        .toList();
  }

  @Override
  public List<AutomationRule> listRules() {
    return rules.values().stream().sorted(BY_PRIORITY_DESC).toList();
  }

  private static RuleFiringDecision decide(AutomationRule rule, AutomationContext context) {
    if (!rule.enabled()) {
      return new RuleFiringDecision.Skipped(rule.ruleId(), "disabled");
    }
    if (!rule.condition().test(context)) {
      return new RuleFiringDecision.Skipped(rule.ruleId(), "condition-false");
    }
    return new RuleFiringDecision.Fired(rule.ruleId(), rule.actions());
  }
}
