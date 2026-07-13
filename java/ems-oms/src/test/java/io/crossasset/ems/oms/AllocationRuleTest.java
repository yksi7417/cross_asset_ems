/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class AllocationRuleTest {

  @Test
  void enumValuesExist() {
    AllocationRule[] values = AllocationRule.values();
    assertEquals(3, values.length);
  }

  @Test
  void proRataAccessible() {
    AllocationRule rule = AllocationRule.PRO_RATA;
    assertNotNull(rule);
    assertEquals("PRO_RATA", rule.name());
  }

  @Test
  void avgPriceAccessible() {
    AllocationRule rule = AllocationRule.AVG_PRICE;
    assertNotNull(rule);
    assertEquals("AVG_PRICE", rule.name());
  }

  @Test
  void sequencedAccessible() {
    AllocationRule rule = AllocationRule.SEQUENCED;
    assertNotNull(rule);
    assertEquals("SEQUENCED", rule.name());
  }
}
