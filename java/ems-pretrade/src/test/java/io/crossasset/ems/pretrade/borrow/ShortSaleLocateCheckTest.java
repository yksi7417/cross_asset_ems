/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.borrow;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.pretrade.compliance.ComplianceCheck.Finding;
import io.crossasset.ems.pretrade.compliance.ComplianceOperation;
import io.crossasset.ems.pretrade.compliance.ComplianceOutcome;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

class ShortSaleLocateCheckTest {

  private static final String HTB_TAG = ShortSaleLocateCheck.HTB_TAG;
  private static final String NAKED_SHORT_OVERRIDE_TAG =
      ShortSaleLocateCheck.NAKED_SHORT_OVERRIDE_TAG;

  private ComplianceOperation operation(String figi, int side) {
    return new ComplianceOperation(
        ComplianceOperation.Kind.STAGE,
        1L,
        "firm",
        "desk",
        "user",
        null,
        figi,
        side,
        100L,
        null,
        "acct");
  }

  @Test
  void sellShortExempt_warns() {
    BorrowService borrow = new BorrowService(60_000L);
    Function<String, Set<String>> tagsOf = user -> Set.of();
    LongSupplier now = () -> 1_000L;

    ShortSaleLocateCheck check = new ShortSaleLocateCheck(borrow, tagsOf, now);
    ComplianceOperation op = operation("BBG_EXEMPT", ShortSaleLocateCheck.SELL_SHORT_EXEMPT);

    var result = check.evaluate(op);

    assertThat(result).isPresent();
    Finding finding = result.get();
    assertThat(finding.outcome()).isEqualTo(ComplianceOutcome.WARN);
    assertThat(finding.overridePath()).isNull();
  }

  @Test
  void buy_notApplicable() {
    BorrowService borrow = new BorrowService(60_000L);
    Function<String, Set<String>> tagsOf = user -> Set.of();
    LongSupplier now = () -> 1_000L;

    ShortSaleLocateCheck check = new ShortSaleLocateCheck(borrow, tagsOf, now);
    ComplianceOperation op = operation("BBG_BUY", 1);

    assertThat(check.evaluate(op)).isEmpty();
  }

  @Test
  void plainSell_notApplicable() {
    BorrowService borrow = new BorrowService(60_000L);
    Function<String, Set<String>> tagsOf = user -> Set.of();
    LongSupplier now = () -> 1_000L;

    ShortSaleLocateCheck check = new ShortSaleLocateCheck(borrow, tagsOf, now);
    ComplianceOperation op = operation("BBG_SELL", 2);

    assertThat(check.evaluate(op)).isEmpty();
  }

  @Test
  void sellShort_hardToBorrowWithoutTag_blocks() {
    BorrowService borrow = new BorrowService(60_000L);
    borrow.setHardToBorrow("BBG_HTB", true);
    Function<String, Set<String>> tagsOf = user -> Set.of();
    LongSupplier now = () -> 1_000L;

    ShortSaleLocateCheck check = new ShortSaleLocateCheck(borrow, tagsOf, now);
    ComplianceOperation op = operation("BBG_HTB", ShortSaleLocateCheck.SELL_SHORT);

    var result = check.evaluate(op);

    assertThat(result).isPresent();
    Finding finding = result.get();
    assertThat(finding.outcome()).isEqualTo(ComplianceOutcome.BLOCK);
    assertThat(finding.rationale()).containsIgnoringCase("hard-to-borrow");
    assertThat(finding.overridePath()).isNotNull();
    assertThat(finding.overridePath().requiredTags()).contains(HTB_TAG);
  }

  @Test
  void sellShort_hardToBorrowWithTag_andAvailability_allows() {
    BorrowService borrow = new BorrowService(60_000L);
    borrow.setHardToBorrow("BBG_HTB_TAGGED", true);
    borrow.recordAvailability("BBG_HTB_TAGGED", "pb", 10_000L, 50L);
    Function<String, Set<String>> tagsOf = user -> Set.of(HTB_TAG);
    LongSupplier now = () -> 1_000L;

    ShortSaleLocateCheck check = new ShortSaleLocateCheck(borrow, tagsOf, now);
    ComplianceOperation op = operation("BBG_HTB_TAGGED", ShortSaleLocateCheck.SELL_SHORT);

    assertThat(check.evaluate(op)).isEmpty();
  }

  @Test
  void sellShort_nakedNoAvailability_blocks() {
    BorrowService borrow = new BorrowService(60_000L);
    Function<String, Set<String>> tagsOf = user -> Set.of();
    LongSupplier now = () -> 1_000L;

    ShortSaleLocateCheck check = new ShortSaleLocateCheck(borrow, tagsOf, now);
    ComplianceOperation op = operation("BBG_NAKED", ShortSaleLocateCheck.SELL_SHORT);

    var result = check.evaluate(op);

    assertThat(result).isPresent();
    Finding finding = result.get();
    assertThat(finding.outcome()).isEqualTo(ComplianceOutcome.BLOCK);
    assertThat(finding.rationale()).containsIgnoringCase("naked");
    assertThat(finding.overridePath()).isNotNull();
    assertThat(finding.overridePath().requiredTags()).contains(NAKED_SHORT_OVERRIDE_TAG);
  }

  @Test
  void sellShort_locatable_allows() {
    BorrowService borrow = new BorrowService(60_000L);
    borrow.recordAvailability("BBG_LOCATABLE", "pb", 10_000L, 50L);
    Function<String, Set<String>> tagsOf = user -> Set.of();
    LongSupplier now = () -> 1_000L;

    ShortSaleLocateCheck check = new ShortSaleLocateCheck(borrow, tagsOf, now);
    ComplianceOperation op = operation("BBG_LOCATABLE", ShortSaleLocateCheck.SELL_SHORT);

    assertThat(check.evaluate(op)).isEmpty();
  }
}
