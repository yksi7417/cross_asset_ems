/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.risk;

import io.crossasset.ems.pretrade.compliance.ComplianceOutcome;
import io.crossasset.ems.pretrade.compliance.OverridePath;
import io.crossasset.ems.pretrade.position.Position;
import io.crossasset.ems.pretrade.position.PositionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Pre-trade position-aware risk engine (task 10.6, arch-risk-engine.md): quantifies the position
 * impact of an operation and compares it against versioned notional caps at account, desk, and firm
 * scope. Hard caps BLOCK (risk-officer override), soft caps WARN; both sides of the decision carry
 * the computed numbers so the audit event reproduces the check.
 *
 * <p>Determinism: the decision is a function of (operation, position snapshot, supplied mark,
 * parameter version) — the mark is an explicit input (no hidden quote read) and the parameter
 * version is recorded on the decision. With no mark, notional impact is incomputable and the engine
 * WARNs ("no_mark") rather than silently allowing — the conservative-upper-bound fallback arrives
 * with the pricing service (10.8). VaR / DV01 / greeks / stress checks land when market scenarios
 * exist; they slot in as additional rows of the same decision shape.
 *
 * <p>Desk/firm aggregation in v1 sums the single instrument's exposure at the wider scopes
 * (cross-instrument portfolio aggregation arrives with the mark engine).
 */
public final class RiskEngine {

  /** One evaluated check row (audit detail). */
  public record CheckResult(
      String check, RiskLimits.Scope scope, long computed, long limit, ComplianceOutcome outcome) {}

  /** The engine's verdict; {@code parameterVersion} pins the limits used. */
  public record RiskDecision(
      ComplianceOutcome decision,
      long parameterVersion,
      List<CheckResult> results,
      @Nullable OverridePath overridePath) {}

  /** The operation under check; side uses FIX tag 54; {@code markPx} nullable when unmarked. */
  public record RiskOperation(
      String firm,
      String desk,
      String account,
      String figi,
      int side,
      long qty,
      @Nullable Long markPx) {}

  private static final OverridePath RISK_OVERRIDE =
      new OverridePath(Set.of("#risk-override-notional"), 2, 3_600_000L, true);

  private final PositionService positions;
  private final RiskLimits limits;

  public RiskEngine(PositionService positions, RiskLimits limits) {
    this.positions = Objects.requireNonNull(positions, "positions");
    this.limits = Objects.requireNonNull(limits, "limits");
  }

  /** Evaluate one operation. Never throws on inputs; unmarked operations WARN. */
  public RiskDecision preTradeCheck(RiskOperation op) {
    long parameterVersion = limits.version();
    List<CheckResult> results = new ArrayList<>();

    if (op.markPx() == null) {
      return new RiskDecision(
          ComplianceOutcome.WARN,
          parameterVersion,
          List.of(
              new CheckResult(
                  "no_mark_notional_incomputable",
                  RiskLimits.Scope.ACCOUNT,
                  0,
                  0,
                  ComplianceOutcome.WARN)),
          null);
    }
    long mark = op.markPx();

    Position current = positions.position(op.account(), op.figi(), null);
    long signedQty = op.side() == 1 ? op.qty() : -op.qty();
    long postNet = Math.abs(current.netQty() + signedQty) * mark;
    long postGross = (Math.abs(current.netQty()) + op.qty()) * mark;

    boolean block = false;
    boolean warn = false;
    for (var scoped :
        List.of(
            new Object[] {RiskLimits.Scope.ACCOUNT, op.account()},
            new Object[] {RiskLimits.Scope.DESK, op.desk()},
            new Object[] {RiskLimits.Scope.FIRM, op.firm()})) {
      RiskLimits.Scope scope = (RiskLimits.Scope) scoped[0];
      String owner = (String) scoped[1];
      var scopeLimits = limits.get(scope, owner);
      if (scopeLimits.isEmpty()) {
        continue;
      }
      RiskLimits.Limits l = scopeLimits.get();
      block |=
          check(
              results,
              "gross_notional_hard",
              scope,
              postGross,
              l.maxGrossNotionalHard(),
              ComplianceOutcome.BLOCK);
      warn |=
          check(
              results,
              "gross_notional_soft",
              scope,
              postGross,
              l.maxGrossNotionalSoft(),
              ComplianceOutcome.WARN);
      block |=
          check(
              results,
              "net_notional_hard",
              scope,
              postNet,
              l.maxNetNotionalHard(),
              ComplianceOutcome.BLOCK);
      warn |=
          check(
              results,
              "net_notional_soft",
              scope,
              postNet,
              l.maxNetNotionalSoft(),
              ComplianceOutcome.WARN);
    }

    if (block) {
      return new RiskDecision(ComplianceOutcome.BLOCK, parameterVersion, results, RISK_OVERRIDE);
    }
    return new RiskDecision(
        warn ? ComplianceOutcome.WARN : ComplianceOutcome.ALLOW, parameterVersion, results, null);
  }

  /** Records the row when a cap exists; returns true when the cap is breached. */
  private static boolean check(
      List<CheckResult> results,
      String name,
      RiskLimits.Scope scope,
      long computed,
      @Nullable Long cap,
      ComplianceOutcome onBreach) {
    if (cap == null) {
      return false;
    }
    boolean breached = computed > cap;
    results.add(
        new CheckResult(name, scope, computed, cap, breached ? onBreach : ComplianceOutcome.ALLOW));
    return breached;
  }
}
