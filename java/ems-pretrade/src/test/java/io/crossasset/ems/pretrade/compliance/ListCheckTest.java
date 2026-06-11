/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ComplianceListService} + {@link ListCheck}: restricted four-eyes block, desk
 * allow-list positive mode, watch warn, effective dates / expiry, versioned change journal. Per
 * arch-compliance.md, task 10.4.
 */
class ListCheckTest {

  private static final String FIGI = "BBG000BLNNH6";
  private static final String OTHER = "BBG000B9XRY4";

  private final long[] now = {1_000L};
  private final ComplianceListService lists = new ComplianceListService();
  private final ListCheck check = new ListCheck(lists, () -> now[0]);

  @Test
  void restrictedInstrument_blocksWithFourEyes() {
    lists.add(ComplianceListService.Kind.RESTRICTED, "firm-a", FIGI, 0L, null);
    Optional<ComplianceCheck.Finding> finding = check.evaluate(op(FIGI));
    assertThat(finding).isPresent();
    assertThat(finding.get().outcome()).isEqualTo(ComplianceOutcome.BLOCK);
    assertThat(finding.get().rationale()).contains("restricted_list");
    assertThat(finding.get().overridePath().requiredSignoffs()).isEqualTo(2);
  }

  @Test
  void restrictedOnOtherFirm_doesNotBlock() {
    lists.add(ComplianceListService.Kind.RESTRICTED, "firm-OTHER", FIGI, 0L, null);
    assertThat(check.evaluate(op(FIGI))).isEmpty();
  }

  @Test
  void allowListMode_blocksUnlisted_allowsListed() {
    lists.setAllowListMode("desk-1", true);
    assertThat(check.evaluate(op(FIGI))).isPresent(); // not on the positive list yet
    lists.add(ComplianceListService.Kind.ALLOW, "desk-1", FIGI, 0L, null);
    assertThat(check.evaluate(op(FIGI))).isEmpty();
    Optional<ComplianceCheck.Finding> other = check.evaluate(op(OTHER));
    assertThat(other).isPresent();
    assertThat(other.get().rationale()).contains("allow_list");
  }

  @Test
  void deskWithoutAllowListMode_unaffected() {
    lists.add(ComplianceListService.Kind.ALLOW, "desk-1", FIGI, 0L, null);
    assertThat(check.evaluate(op(OTHER))).isEmpty();
  }

  @Test
  void watchList_warnsButProceeds() {
    lists.add(ComplianceListService.Kind.WATCH, "firm-a", FIGI, 0L, null);
    Optional<ComplianceCheck.Finding> finding = check.evaluate(op(FIGI));
    assertThat(finding).isPresent();
    assertThat(finding.get().outcome()).isEqualTo(ComplianceOutcome.WARN);
  }

  @Test
  void effectiveDateInFuture_notActiveYet() {
    lists.add(ComplianceListService.Kind.RESTRICTED, "firm-a", FIGI, 5_000L, null);
    assertThat(check.evaluate(op(FIGI))).isEmpty();
    now[0] = 5_000L;
    assertThat(check.evaluate(op(FIGI))).isPresent();
  }

  @Test
  void expiredEntry_stopsBlocking() {
    lists.add(ComplianceListService.Kind.RESTRICTED, "firm-a", FIGI, 0L, 2_000L);
    assertThat(check.evaluate(op(FIGI))).isPresent();
    now[0] = 2_000L;
    assertThat(check.evaluate(op(FIGI))).isEmpty();
  }

  @Test
  void removal_stopsBlocking() {
    lists.add(ComplianceListService.Kind.RESTRICTED, "firm-a", FIGI, 0L, null);
    lists.remove(ComplianceListService.Kind.RESTRICTED, "firm-a", FIGI);
    assertThat(check.evaluate(op(FIGI))).isEmpty();
  }

  @Test
  void everyMutation_versionsAndJournals() {
    long v1 = lists.add(ComplianceListService.Kind.RESTRICTED, "firm-a", FIGI, 0L, null);
    long v2 = lists.setAllowListMode("desk-1", true);
    long v3 = lists.remove(ComplianceListService.Kind.RESTRICTED, "firm-a", FIGI);
    assertThat(v1).isEqualTo(1);
    assertThat(v2).isEqualTo(2);
    assertThat(v3).isEqualTo(3);
    assertThat(lists.journal()).hasSize(3);
    assertThat(lists.journal().get(0).action()).isEqualTo("ADD");
    // No-op mutations do not version.
    assertThat(lists.remove(ComplianceListService.Kind.RESTRICTED, "firm-a", FIGI)).isEqualTo(3);
    assertThat(lists.journal()).hasSize(3);
  }

  @Test
  void integratesWithGate_restrictedBlocksViaGate() {
    lists.add(ComplianceListService.Kind.RESTRICTED, "firm-a", FIGI, 0L, null);
    ComplianceGate gate = new ComplianceGate(java.util.List.of(check));
    ComplianceDecision decision = gate.evaluate(op(FIGI));
    assertThat(decision.outcome()).isEqualTo(ComplianceOutcome.BLOCK);
    assertThat(gate.pendingBlocks()).hasSize(1);
  }

  private static ComplianceOperation op(String figi) {
    return new ComplianceOperation(
        ComplianceOperation.Kind.STAGE,
        1L,
        "firm-a",
        "desk-1",
        "trader-1",
        null,
        figi,
        1,
        100,
        null,
        "acc-1");
  }
}
