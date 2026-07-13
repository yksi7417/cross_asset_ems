/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ComplianceCheckTest {

  private static final OverridePath FOUR_EYES =
      new OverridePath(Set.of("compliance"), 2, 60_000L, true);

  private static ComplianceOperation stageOp() {
    return new ComplianceOperation(
        ComplianceOperation.Kind.STAGE,
        7L,
        "firm-1",
        "desk-1",
        "user-1",
        null,
        "BBG000BLNNH6",
        1,
        100_00L,
        null,
        "acct-1");
  }

  @Test
  void findingStoresAllFields() {
    var f = new ComplianceCheck.Finding(ComplianceOutcome.BLOCK, "over limit", FOUR_EYES);
    assertEquals(ComplianceOutcome.BLOCK, f.outcome());
    assertEquals("over limit", f.rationale());
    assertEquals(FOUR_EYES, f.overridePath());
  }

  @Test
  void warnNeedsNoOverridePath() {
    var f = new ComplianceCheck.Finding(ComplianceOutcome.WARN, "watch out", null);
    assertEquals(ComplianceOutcome.WARN, f.outcome());
    assertNull(f.overridePath());
  }

  @Test
  void blockWithoutOverridePathRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ComplianceCheck.Finding(ComplianceOutcome.BLOCK, "over limit", null));
  }

  @Test
  void allowFindingRejected() {
    // ALLOW is expressed by evaluate() returning empty, never as a Finding
    assertThrows(
        IllegalArgumentException.class,
        () -> new ComplianceCheck.Finding(ComplianceOutcome.ALLOW, "fine", null));
  }

  @Test
  void nullOutcomeRejected() {
    assertThrows(
        NullPointerException.class, () -> new ComplianceCheck.Finding(null, "r", FOUR_EYES));
  }

  @Test
  void nullRationaleRejected() {
    assertThrows(
        NullPointerException.class,
        () -> new ComplianceCheck.Finding(ComplianceOutcome.WARN, null, null));
  }

  @Test
  void allowingRuleReturnsEmpty() {
    ComplianceCheck allowAll =
        new ComplianceCheck() {
          @Override
          public String ruleId() {
            return "allow-all";
          }

          @Override
          public Optional<Finding> evaluate(ComplianceOperation operation) {
            return Optional.empty();
          }
        };

    assertEquals("allow-all", allowAll.ruleId());
    assertTrue(allowAll.evaluate(stageOp()).isEmpty());
  }

  @Test
  void blockingRuleSurfacesItsFinding() {
    ComplianceCheck blockAll =
        new ComplianceCheck() {
          @Override
          public String ruleId() {
            return "block-all";
          }

          @Override
          public Optional<Finding> evaluate(ComplianceOperation operation) {
            return Optional.of(
                new Finding(ComplianceOutcome.BLOCK, "blocked: " + operation.figi(), FOUR_EYES));
          }
        };

    var finding = blockAll.evaluate(stageOp()).orElseThrow();
    assertEquals(ComplianceOutcome.BLOCK, finding.outcome());
    assertEquals("blocked: BBG000BLNNH6", finding.rationale());
    assertEquals(FOUR_EYES, finding.overridePath());
  }
}
