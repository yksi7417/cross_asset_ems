/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue.sor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The smart order router (task 11.11, [[arch-smart-order-router]]): a registry of versioned {@link
 * SorStrategy} implementations and the audit chain around them — every routing decision is logged
 * with the strategy, its version, and the plan's rationale, so compliance can reconstruct why venue
 * X received the order (the [[arch-best-execution]] § routing-decision evidence; feed these
 * straight into {@code BestExAuditor.recordDecision}).
 */
public final class SmartOrderRouter {

  /** One logged selection — the audit chain entry. */
  public record SelectionEvent(
      String routeId,
      String strategyId,
      int strategyVersion,
      SorStrategy.CascadePlan plan,
      long atMillis) {}

  private final Map<String, SorStrategy> strategies = new LinkedHashMap<>();
  private final List<SelectionEvent> selections = new ArrayList<>();

  public void register(SorStrategy strategy) {
    strategies.put(strategy.id(), Objects.requireNonNull(strategy));
  }

  /** Route one intent via the named strategy; the decision is logged before it is returned. */
  public SorStrategy.CascadePlan route(
      SorStrategy.RouteIntent intent, String strategyId, SorStrategy.MarketContext context) {
    SorStrategy strategy = strategies.get(strategyId);
    if (strategy == null) {
      throw new IllegalArgumentException("unknown SOR strategy " + strategyId);
    }
    SorStrategy.CascadePlan plan = strategy.decide(intent, context);
    selections.add(
        new SelectionEvent(
            intent.routeId(), strategy.id(), strategy.version(), plan, context.nowMillis()));
    return plan;
  }

  /** Every selection ever made, in decision order (the compliance reconstruction surface). */
  public List<SelectionEvent> selections() {
    return Collections.unmodifiableList(selections);
  }
}
