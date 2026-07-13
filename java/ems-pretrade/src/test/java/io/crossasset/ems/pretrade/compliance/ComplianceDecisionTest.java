/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ComplianceDecisionTest {

  @Test
  void recordStoresAllFields() {
    var rr = new ComplianceDecision.RuleResult("rule-1", ComplianceOutcome.BLOCK, "blocked");
    var decision =
        new ComplianceDecision(ComplianceOutcome.BLOCK, "check-1", "block-123", List.of(rr));

    assertEquals(ComplianceOutcome.BLOCK, decision.outcome());
    assertEquals("check-1", decision.checkId());
    assertEquals("block-123", decision.blockId());
    assertEquals(1, decision.ruleResults().size());
    assertEquals("rule-1", decision.ruleResults().get(0).ruleId());
  }

  @Test
  void ruleResultFields() {
    var rr = new ComplianceDecision.RuleResult("r1", ComplianceOutcome.WARN, "watch out");
    assertEquals("r1", rr.ruleId());
    assertEquals(ComplianceOutcome.WARN, rr.outcome());
    assertEquals("watch out", rr.rationale());
  }

  @Test
  void nullOutcomeRejected() {
    assertThrows(
        NullPointerException.class, () -> new ComplianceDecision(null, "check-1", null, List.of()));
  }

  @Test
  void nullCheckIdRejected() {
    assertThrows(
        NullPointerException.class,
        () -> new ComplianceDecision(ComplianceOutcome.ALLOW, null, null, List.of()));
  }

  @Test
  void nullRuleResultsRejected() {
    assertThrows(
        NullPointerException.class,
        () -> new ComplianceDecision(ComplianceOutcome.ALLOW, "c", null, null));
  }

  @Test
  void ruleResultsIsUnmodifiableCopy() {
    var rr = new ComplianceDecision.RuleResult("r1", ComplianceOutcome.ALLOW, "ok");
    var mutable = List.of(rr);
    var decision = new ComplianceDecision(ComplianceOutcome.ALLOW, "c", null, mutable);

    assertThrows(UnsupportedOperationException.class, () -> decision.ruleResults().add(rr));
  }

  @Test
  void blockIdNullable() {
    var decision = new ComplianceDecision(ComplianceOutcome.ALLOW, "c", null, List.of());
    assertNotNull(decision);
    assertTrue(decision.blockId() == null);
  }

  @Test
  void equalityAndHash() {
    var rr1 = new ComplianceDecision.RuleResult("r1", ComplianceOutcome.BLOCK, "b");
    var rr2 = new ComplianceDecision.RuleResult("r1", ComplianceOutcome.BLOCK, "b");
    var d1 = new ComplianceDecision(ComplianceOutcome.BLOCK, "c", "b1", List.of(rr1));
    var d2 = new ComplianceDecision(ComplianceOutcome.BLOCK, "c", "b1", List.of(rr2));

    assertEquals(d1, d2);
    assertEquals(d1.hashCode(), d2.hashCode());
  }

  @Test
  void inequalityOnDifferentOutcome() {
    var rr = new ComplianceDecision.RuleResult("r1", ComplianceOutcome.BLOCK, "b");
    var d1 = new ComplianceDecision(ComplianceOutcome.BLOCK, "c", "b1", List.of(rr));
    var d2 = new ComplianceDecision(ComplianceOutcome.WARN, "c", "b1", List.of(rr));

    assertNotEquals(d1, d2);
  }
}
