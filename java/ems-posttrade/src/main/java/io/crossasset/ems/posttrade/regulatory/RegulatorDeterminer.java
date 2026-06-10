/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Determines which regulators a trade is reportable to (per arch-regulatory-reporting-service.md §
 * Determination logic): a lookup matrix of {@code (condition → regulator)} rules, each emitted
 * independently. Pure function of the trade and the matrix version, so replay reproduces the same
 * regulator set.
 */
public final class RegulatorDeterminer {

  private record Rule(Predicate<ReportableTrade> condition, Regulator regulator) {}

  private final List<Rule> matrix = new ArrayList<>();

  /** Add a rule: any trade matching {@code condition} is reportable to {@code regulator}. */
  public RegulatorDeterminer addRule(Predicate<ReportableTrade> condition, Regulator regulator) {
    matrix.add(new Rule(condition, regulator));
    return this;
  }

  /** The regulators a trade is reportable to, in matrix order, de-duplicated. */
  public List<Regulator> applicableRegulators(ReportableTrade trade) {
    List<Regulator> out = new ArrayList<>();
    for (Rule rule : matrix) {
      if (rule.condition().test(trade) && !out.contains(rule.regulator())) {
        out.add(rule.regulator());
      }
    }
    return out;
  }

  /** US default matrix for the MVP: IG corp bonds report to TRACE. */
  public static RegulatorDeterminer usDefaults() {
    return new RegulatorDeterminer()
        .addRule(
            t -> "US".equals(t.jurisdiction()) && "CORP_BOND".equals(t.assetClass()),
            Regulator.TRACE);
  }
}
