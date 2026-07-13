/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ComplianceOutcomeTest {

  @Test
  void enumValues() {
    assertNotNull(ComplianceOutcome.ALLOW);
    assertNotNull(ComplianceOutcome.BLOCK);
    assertNotNull(ComplianceOutcome.WARN);
    assertEquals(3, ComplianceOutcome.values().length);
  }

  @Test
  void enumName() {
    assertEquals("ALLOW", ComplianceOutcome.ALLOW.name());
    assertEquals("BLOCK", ComplianceOutcome.BLOCK.name());
    assertEquals("WARN", ComplianceOutcome.WARN.name());
  }
}
