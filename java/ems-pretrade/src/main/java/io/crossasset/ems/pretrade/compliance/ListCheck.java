/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.compliance;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * The list gate (task 10.4): firm restricted list → BLOCK with a four-eyes override; desk
 * allow-list mode → BLOCK when the instrument is not on the positive list; firm watch list → WARN
 * (heightened monitoring, operation proceeds). Applies to every gated operation kind — list
 * membership is an instrument property, not an action property. Clock injected for effective-date
 * and expiry evaluation under replay.
 */
public final class ListCheck implements ComplianceCheck {

  private static final OverridePath RESTRICTED_OVERRIDE =
      new OverridePath(Set.of("#compliance-override-restricted-instrument"), 2, 3_600_000L, true);
  private static final OverridePath ALLOW_LIST_OVERRIDE =
      new OverridePath(Set.of("#compliance-override-add-to-allow-list"), 1, 3_600_000L, true);

  private final ComplianceListService lists;
  private final LongSupplier clockMillis;

  public ListCheck(ComplianceListService lists, LongSupplier clockMillis) {
    this.lists = Objects.requireNonNull(lists, "lists");
    this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis");
  }

  @Override
  public String ruleId() {
    return "lists";
  }

  @Override
  public Optional<Finding> evaluate(ComplianceOperation op) {
    long now = clockMillis.getAsLong();
    if (lists.isActive(ComplianceListService.Kind.RESTRICTED, op.firm(), op.figi(), now)) {
      return Optional.of(
          new Finding(
              ComplianceOutcome.BLOCK,
              "instrument_on_restricted_list: " + op.figi() + " (firm " + op.firm() + ")",
              RESTRICTED_OVERRIDE));
    }
    if (lists.usesAllowList(op.desk())
        && !lists.isActive(ComplianceListService.Kind.ALLOW, op.desk(), op.figi(), now)) {
      return Optional.of(
          new Finding(
              ComplianceOutcome.BLOCK,
              "instrument_not_on_desk_allow_list: " + op.figi() + " (desk " + op.desk() + ")",
              ALLOW_LIST_OVERRIDE));
    }
    if (lists.isActive(ComplianceListService.Kind.WATCH, op.firm(), op.figi(), now)) {
      return Optional.of(
          new Finding(
              ComplianceOutcome.WARN,
              "instrument_on_watch_list: " + op.figi() + " (firm " + op.firm() + ")",
              null));
    }
    return Optional.empty();
  }
}
