/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.compliance;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Machine-gun rate limiter (task 10.3, arch-compliance § Machine-gun check): catches flows that
 * stay under per-order caps but explode in aggregate. Three rules over rolling windows keyed by
 * actor (firm|desk) and, for routing, the (instrument, side) signature:
 *
 * <ol>
 *   <li>route count per window,
 *   <li>aggregated notional per window (limit-priced flow; market orders contribute qty-only and
 *       are caught by rule 1 until reference pricing (9.5/10.8) supplies a mark),
 *   <li>cancel/replace churn per window (layering/spoofing hand-off to surveillance, 12.15).
 * </ol>
 *
 * <p>Only attempts that pass are recorded — a blocked burst doesn't poison the window. The clock is
 * injected (sim-clock under replay). History is the in-memory realization of the rolling counter
 * projection; per-firm tunables arrive via the configuration service.
 */
public final class MachineGunCheck implements ComplianceCheck {

  /** Window + thresholds; one policy per gate instance (per-firm config later). */
  public record Policy(
      long windowMillis,
      int maxRoutesPerWindow,
      long maxAggregatedNotionalPerWindow,
      int maxReplacesPerWindow) {}

  private static final OverridePath RATE_OVERRIDE =
      new OverridePath(java.util.Set.of("#compliance-override-rate-limit"), 1, 3_600_000L, true);

  private record Attempt(long atMillis, long notional) {}

  private final Policy policy;
  private final LongSupplier clockMillis;
  private final ConcurrentHashMap<String, Deque<Attempt>> routeHistory = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Deque<Attempt>> replaceHistory =
      new ConcurrentHashMap<>();

  public MachineGunCheck(Policy policy, LongSupplier clockMillis) {
    this.policy = Objects.requireNonNull(policy, "policy");
    this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis");
  }

  @Override
  public String ruleId() {
    return "machine-gun";
  }

  @Override
  public Optional<Finding> evaluate(ComplianceOperation op) {
    long now = clockMillis.getAsLong();
    return switch (op.kind()) {
      case ROUTE -> evaluateRoute(op, now);
      case AMEND -> evaluateReplaceChurn(op, now);
      case STAGE, MARK_READY -> Optional.empty();
    };
  }

  private Optional<Finding> evaluateRoute(ComplianceOperation op, long now) {
    String key = op.firm() + "|" + op.desk() + "|" + op.figi() + "|" + op.side();
    Deque<Attempt> window = routeHistory.computeIfAbsent(key, k -> new ArrayDeque<>());
    synchronized (window) {
      prune(window, now);
      if (window.size() + 1 > policy.maxRoutesPerWindow()) {
        return Optional.of(
            new Finding(
                ComplianceOutcome.BLOCK,
                "machine_gun_route_count_exceeded: "
                    + (window.size() + 1)
                    + " routes in "
                    + policy.windowMillis()
                    + "ms (max "
                    + policy.maxRoutesPerWindow()
                    + ")",
                RATE_OVERRIDE));
      }
      long attemptNotional = op.price() == null ? op.qty() : op.qty() * op.price();
      long aggregated = window.stream().mapToLong(Attempt::notional).sum() + attemptNotional;
      if (aggregated > policy.maxAggregatedNotionalPerWindow()) {
        return Optional.of(
            new Finding(
                ComplianceOutcome.BLOCK,
                "machine_gun_aggregated_notional_exceeded: "
                    + aggregated
                    + " in window (max "
                    + policy.maxAggregatedNotionalPerWindow()
                    + ")",
                RATE_OVERRIDE));
      }
      window.addLast(new Attempt(now, attemptNotional));
      return Optional.empty();
    }
  }

  private Optional<Finding> evaluateReplaceChurn(ComplianceOperation op, long now) {
    String key = op.firm() + "|" + op.desk();
    Deque<Attempt> window = replaceHistory.computeIfAbsent(key, k -> new ArrayDeque<>());
    synchronized (window) {
      prune(window, now);
      if (window.size() + 1 > policy.maxReplacesPerWindow()) {
        return Optional.of(
            new Finding(
                ComplianceOutcome.BLOCK,
                "cancel_replace_churn: "
                    + (window.size() + 1)
                    + " replaces in window (max "
                    + policy.maxReplacesPerWindow()
                    + ")",
                RATE_OVERRIDE));
      }
      window.addLast(new Attempt(now, 0));
      return Optional.empty();
    }
  }

  private void prune(Deque<Attempt> window, long now) {
    while (!window.isEmpty() && now - window.peekFirst().atMillis() >= policy.windowMillis()) {
      window.removeFirst();
    }
  }
}
