/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.borrow;

import io.crossasset.ems.pretrade.compliance.ComplianceCheck;
import io.crossasset.ems.pretrade.compliance.ComplianceOperation;
import io.crossasset.ems.pretrade.compliance.ComplianceOutcome;
import io.crossasset.ems.pretrade.compliance.OverridePath;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Reg SHO short-sale gate (task 18.6): the compliance-path face of the borrow service. On every
 * SELL_SHORT stage/mark-ready, a locate is sourced through {@link BorrowService} — located shorts
 * pass with the locate documented in the borrow journal (Rule 203(b) affirmative determination);
 * naked shorts BLOCK with the heavily-audited {@code #compliance-override-naked-short} path.
 * Hard-to-borrow names additionally require the trader to hold {@code #htb-short-permitted}.
 * SELL_SHORT_EXEMPT (FIX side 6) passes with a WARN so the exemption is visible in the audit.
 */
public final class ShortSaleLocateCheck implements ComplianceCheck {

  /** FIX side 5. */
  public static final int SELL_SHORT = 5;

  /** FIX side 6. */
  public static final int SELL_SHORT_EXEMPT = 6;

  /** Tag allowing HTB shorting (arch-borrow-service § HTB). */
  public static final String HTB_TAG = "#htb-short-permitted";

  /** Override tag for naked-short blocks (arch-borrow-service § Pre-trade locate flow). */
  public static final String NAKED_SHORT_OVERRIDE_TAG = "#compliance-override-naked-short";

  private static final long OVERRIDE_EXPIRY_MILLIS = 15 * 60 * 1000;

  private final BorrowService borrow;
  private final Function<String, Set<String>> tagsOf;
  private final LongSupplier nowMillis;

  /**
   * @param tagsOf resolves a user ID to effective permission tags (AAA-backed in production)
   */
  public ShortSaleLocateCheck(
      BorrowService borrow, Function<String, Set<String>> tagsOf, LongSupplier nowMillis) {
    this.borrow = Objects.requireNonNull(borrow, "borrow");
    this.tagsOf = Objects.requireNonNull(tagsOf, "tagsOf");
    this.nowMillis = Objects.requireNonNull(nowMillis, "nowMillis");
  }

  @Override
  public String ruleId() {
    return "reg-sho-locate";
  }

  @Override
  public Optional<Finding> evaluate(ComplianceOperation operation) {
    if (operation.side() == SELL_SHORT_EXEMPT) {
      return Optional.of(
          new Finding(
              ComplianceOutcome.WARN,
              "SELL_SHORT_EXEMPT: locate not required; exemption basis must be documented.",
              null));
    }
    if (operation.side() != SELL_SHORT) {
      return Optional.empty();
    }
    if (borrow.isHardToBorrow(operation.figi())
        && !tagsOf.apply(operation.user()).contains(HTB_TAG)) {
      return Optional.of(
          new Finding(
              ComplianceOutcome.BLOCK,
              "Hard-to-borrow name: shorting " + operation.figi() + " requires " + HTB_TAG + ".",
              new OverridePath(Set.of(HTB_TAG), 1, OVERRIDE_EXPIRY_MILLIS, true)));
    }
    BorrowService.LocateResult result =
        borrow.locate(operation.figi(), operation.qty(), operation.user(), nowMillis.getAsLong());
    if (result instanceof BorrowService.LocateResult.NotLocated notLocated) {
      return Optional.of(
          new Finding(
              ComplianceOutcome.BLOCK,
              "No locate (naked short blocked): " + notLocated.reason(),
              new OverridePath(Set.of(NAKED_SHORT_OVERRIDE_TAG), 1, OVERRIDE_EXPIRY_MILLIS, true)));
    }
    // Located: allow. The LocateRecord in the borrow journal is the documented determination.
    return Optional.empty();
  }
}
