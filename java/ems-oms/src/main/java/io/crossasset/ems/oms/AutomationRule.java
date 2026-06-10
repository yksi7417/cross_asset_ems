/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import java.util.List;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * Immutable rule definition evaluated by the {@link AutomationEngine}.
 *
 * <p>Rules are keyed by {@code ruleId}, scoped to a {@link AutomationScope} + optional {@code
 * scopeRef} (e.g. desk ID, user ID), filtered by {@code triggerEvent}, and carry a {@code
 * condition} predicate evaluated against the live {@link AutomationContext}. When both event and
 * condition match, the rule fires and produces its {@code actions}.
 *
 * <p>Priority is descending — higher values evaluated first. Disabled rules are always skipped.
 */
public record AutomationRule(
    String ruleId,
    AutomationScope scope,
    @Nullable String scopeRef,
    String triggerEvent,
    Predicate<AutomationContext> condition,
    List<AutomationAction> actions,
    int priority,
    boolean enabled) {

  /** Convenience constructor that matches any context (condition = always true). */
  public AutomationRule(
      String ruleId,
      AutomationScope scope,
      @Nullable String scopeRef,
      String triggerEvent,
      List<AutomationAction> actions,
      int priority,
      boolean enabled) {
    this(ruleId, scope, scopeRef, triggerEvent, ctx -> true, actions, priority, enabled);
  }
}
