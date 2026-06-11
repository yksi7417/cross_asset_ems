/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MachineGunCheck}: count threshold, aggregated notional, cancel-replace churn,
 * window expiry, actor isolation, no poisoning by blocked attempts. Per arch-compliance.md, task
 * 10.3.
 */
class MachineGunCheckTest {

  private static final MachineGunCheck.Policy POLICY =
      new MachineGunCheck.Policy(60_000L, 3, 1_000_000L, 2);

  private final long[] now = {0L};
  private final MachineGunCheck check = new MachineGunCheck(POLICY, () -> now[0]);

  @Test
  void routesUnderCount_allow() {
    assertThat(check.evaluate(route(100, null))).isEmpty();
    assertThat(check.evaluate(route(100, null))).isEmpty();
    assertThat(check.evaluate(route(100, null))).isEmpty();
  }

  @Test
  void fourthRouteInWindow_blocks() {
    for (int i = 0; i < 3; i++) {
      check.evaluate(route(100, null));
    }
    Optional<ComplianceCheck.Finding> finding = check.evaluate(route(100, null));
    assertThat(finding).isPresent();
    assertThat(finding.get().outcome()).isEqualTo(ComplianceOutcome.BLOCK);
    assertThat(finding.get().rationale()).contains("machine_gun_route_count_exceeded");
    assertThat(finding.get().overridePath().requiredTags())
        .contains("#compliance-override-rate-limit");
  }

  @Test
  void windowExpiry_resetsCounter() {
    for (int i = 0; i < 3; i++) {
      check.evaluate(route(100, null));
    }
    now[0] = 60_000L; // first window fully aged out
    assertThat(check.evaluate(route(100, null))).isEmpty();
  }

  @Test
  void aggregatedNotional_blocksDespiteLowCount() {
    // Two limit routes of 600k notional each: count fine (2 <= 3), aggregate 1.2M > 1M.
    assertThat(check.evaluate(route(60, 10_000L))).isEmpty();
    Optional<ComplianceCheck.Finding> finding = check.evaluate(route(60, 10_000L));
    assertThat(finding).isPresent();
    assertThat(finding.get().rationale()).contains("aggregated_notional");
  }

  @Test
  void replaceChurn_blocksAfterMax() {
    assertThat(check.evaluate(amend())).isEmpty();
    assertThat(check.evaluate(amend())).isEmpty();
    Optional<ComplianceCheck.Finding> finding = check.evaluate(amend());
    assertThat(finding).isPresent();
    assertThat(finding.get().rationale()).contains("cancel_replace_churn");
  }

  @Test
  void differentDesk_isolatedWindows() {
    for (int i = 0; i < 3; i++) {
      check.evaluate(route(100, null));
    }
    ComplianceOperation otherDesk =
        new ComplianceOperation(
            ComplianceOperation.Kind.ROUTE,
            1L,
            "firm-a",
            "desk-OTHER",
            "trader-2",
            "EMS-ORD-1",
            "BBG000BLNNH6",
            1,
            100,
            null,
            "acc-1");
    assertThat(check.evaluate(otherDesk)).isEmpty();
  }

  @Test
  void blockedAttempt_doesNotPoisonWindow() {
    for (int i = 0; i < 3; i++) {
      check.evaluate(route(100, null));
    }
    assertThat(check.evaluate(route(100, null))).isPresent(); // blocked, not recorded
    now[0] = 60_000L; // original three age out; the blocked one must not linger
    assertThat(check.evaluate(route(100, null))).isEmpty();
  }

  @Test
  void stage_notGatedByThisRule() {
    ComplianceOperation stage =
        new ComplianceOperation(
            ComplianceOperation.Kind.STAGE,
            1L,
            "firm-a",
            "desk-1",
            "trader-1",
            null,
            "BBG000BLNNH6",
            1,
            100,
            null,
            "acc-1");
    for (int i = 0; i < 10; i++) {
      assertThat(check.evaluate(stage)).isEmpty();
    }
  }

  private static ComplianceOperation route(long qty, @Nullable Long price) {
    return new ComplianceOperation(
        ComplianceOperation.Kind.ROUTE,
        1L,
        "firm-a",
        "desk-1",
        "trader-1",
        "EMS-ORD-1",
        "BBG000BLNNH6",
        1,
        qty,
        price,
        "acc-1");
  }

  private static ComplianceOperation amend() {
    return new ComplianceOperation(
        ComplianceOperation.Kind.AMEND,
        1L,
        "firm-a",
        "desk-1",
        "trader-1",
        "EMS-ORD-1",
        "BBG000BLNNH6",
        1,
        100,
        null,
        "acc-1");
  }
}
