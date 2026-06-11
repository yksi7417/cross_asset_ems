/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link OverrideService}: tag qualification, distinct sign-off counting (four-eyes),
 * rationale enforcement, deny, time-bound release validity. Per arch-compliance.md, task 10.5.
 */
class OverrideServiceTest {

  private static final String TAG = "#compliance-override-restricted-instrument";

  private final long[] now = {10_000L};
  private final ComplianceGate gate =
      new ComplianceGate(
          List.of(
              new ComplianceCheck() {
                @Override
                public String ruleId() {
                  return "always-block";
                }

                @Override
                public Optional<Finding> evaluate(ComplianceOperation operation) {
                  return Optional.of(
                      new Finding(
                          ComplianceOutcome.BLOCK,
                          "test block",
                          new OverridePath(Set.of(TAG), 2, 60_000L, true)));
                }
              }));

  /** supervisors hold the override tag; traders do not. */
  private final OverrideService overrides =
      new OverrideService(
          gate, (firm, desk, user, tag) -> user.startsWith("supervisor"), () -> now[0]);

  private String blockedOperation() {
    return gate.evaluate(
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
                "acc-1"))
        .blockId();
  }

  @Test
  void unqualifiedApprover_rejected() {
    String blockId = blockedOperation();
    OverrideService.OverrideResult result =
        overrides.approve(blockId, "firm-a", "desk-1", "trader-2", "looks fine");
    assertThat(result).isInstanceOf(OverrideService.OverrideResult.Rejected.class);
    assertThat(((OverrideService.OverrideResult.Rejected) result).reason()).contains(TAG);
    assertThat(gate.findBlock(blockId).orElseThrow().status())
        .isEqualTo(PendingBlock.Status.PENDING);
  }

  @Test
  void fourEyes_firstApprovalPends_secondReleases() {
    String blockId = blockedOperation();
    OverrideService.OverrideResult first =
        overrides.approve(blockId, "firm-a", "desk-1", "supervisor-1", "verified");
    OverrideService.OverrideResult.Approved approved =
        (OverrideService.OverrideResult.Approved) first;
    assertThat(approved.signoffsSoFar()).isEqualTo(1);
    assertThat(approved.signoffsRequired()).isEqualTo(2);
    assertThat(gate.findBlock(blockId).orElseThrow().status())
        .isEqualTo(PendingBlock.Status.PENDING);

    OverrideService.OverrideResult second =
        overrides.approve(blockId, "firm-a", "desk-1", "supervisor-2", "also verified");
    OverrideService.OverrideResult.Released released =
        (OverrideService.OverrideResult.Released) second;
    assertThat(released.block().status()).isEqualTo(PendingBlock.Status.RELEASED);
    assertThat(released.validUntilMillis()).isEqualTo(70_000L); // now + 60s expiry
  }

  @Test
  void sameApproverTwice_rejected() {
    String blockId = blockedOperation();
    overrides.approve(blockId, "firm-a", "desk-1", "supervisor-1", "verified");
    OverrideService.OverrideResult again =
        overrides.approve(blockId, "firm-a", "desk-1", "supervisor-1", "again");
    assertThat(again).isInstanceOf(OverrideService.OverrideResult.Rejected.class);
    assertThat(((OverrideService.OverrideResult.Rejected) again).reason()).contains("distinct");
  }

  @Test
  void missingRationale_rejectedWhenRequired() {
    String blockId = blockedOperation();
    OverrideService.OverrideResult result =
        overrides.approve(blockId, "firm-a", "desk-1", "supervisor-1", "  ");
    assertThat(result).isInstanceOf(OverrideService.OverrideResult.Rejected.class);
    assertThat(((OverrideService.OverrideResult.Rejected) result).reason()).contains("rationale");
  }

  @Test
  void deny_singleQualifiedDenier_closesBlock() {
    String blockId = blockedOperation();
    OverrideService.OverrideResult result =
        overrides.deny(blockId, "firm-a", "desk-1", "supervisor-1", "not appropriate");
    assertThat(result).isInstanceOf(OverrideService.OverrideResult.Denied.class);
    assertThat(gate.findBlock(blockId).orElseThrow().status())
        .isEqualTo(PendingBlock.Status.DENIED);

    OverrideService.OverrideResult after =
        overrides.approve(blockId, "firm-a", "desk-1", "supervisor-2", "try anyway");
    assertThat(after).isInstanceOf(OverrideService.OverrideResult.Rejected.class);
  }

  @Test
  void release_isTimeBound() {
    String blockId = blockedOperation();
    overrides.approve(blockId, "firm-a", "desk-1", "supervisor-1", "ok");
    overrides.approve(blockId, "firm-a", "desk-1", "supervisor-2", "ok");
    assertThat(overrides.isReleaseValid(blockId, 69_999L)).isTrue();
    assertThat(overrides.isReleaseValid(blockId, 70_000L)).isFalse();
  }

  @Test
  void unknownBlock_rejected() {
    OverrideService.OverrideResult result =
        overrides.approve("NOPE", "firm-a", "desk-1", "supervisor-1", "x");
    assertThat(result).isInstanceOf(OverrideService.OverrideResult.Rejected.class);
  }

  @Test
  void unreleasedBlock_neverValid() {
    String blockId = blockedOperation();
    assertThat(overrides.isReleaseValid(blockId, now[0])).isFalse();
  }
}
