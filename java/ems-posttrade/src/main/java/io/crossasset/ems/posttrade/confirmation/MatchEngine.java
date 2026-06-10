/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.confirmation;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure confirmation match engine. Compares our trade record against the counterparty's under a
 * {@link MatchTolerance}; keyed fields must be equal, numeric fields must be within tolerance.
 * Being a pure function of {@code (ours, theirs, tolerance)} it reproduces identical match outcomes
 * under replay (arch-confirmation-affirmation.md § Determinism).
 */
public final class MatchEngine {

  private MatchEngine() {}

  public static MatchResult match(TradeRecord ours, TradeRecord theirs, MatchTolerance tol) {
    List<String> diffs = new ArrayList<>();
    if (!ours.instrumentId().equals(theirs.instrumentId())) {
      diffs.add("instrumentId");
    }
    if (ours.side() != theirs.side()) {
      diffs.add("side");
    }
    if (!equalsNullable(ours.tradeDate(), theirs.tradeDate())) {
      diffs.add("tradeDate");
    }
    if (!equalsNullable(ours.settleDate(), theirs.settleDate())) {
      diffs.add("settleDate");
    }
    if (!equalsNullable(ours.counterparty(), theirs.counterparty())) {
      diffs.add("counterparty");
    }
    if (Math.abs(ours.qty() - theirs.qty()) > tol.qtyTolerance()) {
      diffs.add("qty");
    }
    if (Math.abs(ours.price() - theirs.price()) > tol.priceTolerance()) {
      diffs.add("price");
    }
    if (Math.abs(ours.accrued() - theirs.accrued()) > tol.accruedTolerance()) {
      diffs.add("accrued");
    }
    return diffs.isEmpty() ? MatchResult.ofMatch() : MatchResult.ofMismatch(diffs);
  }

  private static boolean equalsNullable(String a, String b) {
    return a == null ? b == null : a.equals(b);
  }
}
