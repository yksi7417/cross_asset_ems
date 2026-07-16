/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class RuleFiringDecisionTest {

  @Test
  void testSkippedRecord() {
    RuleFiringDecision.Skipped skipped =
        new RuleFiringDecision.Skipped("rule-1", "condition false");
    assertNotNull(skipped);
    assertEquals("rule-1", skipped.ruleId());
    assertEquals("condition false", skipped.reason());
  }

  @Test
  void testClassExists() {
    assertNotNull(RuleFiringDecision.class);
  }
}
