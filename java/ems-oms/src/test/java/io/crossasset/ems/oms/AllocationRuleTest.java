/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AllocationRuleTest {

  @Test
  void shouldHaveThreeValues() {
    AllocationRule[] values = AllocationRule.values();
    assertEquals(3, values.length);
  }

  @Test
  void shouldContainExpectedValues() {
    AllocationRule[] values = AllocationRule.values();
    assertTrue(java.util.Arrays.asList(values).contains(AllocationRule.PRO_RATA));
    assertTrue(java.util.Arrays.asList(values).contains(AllocationRule.AVG_PRICE));
    assertTrue(java.util.Arrays.asList(values).contains(AllocationRule.SEQUENCED));
  }

  @Test
  void shouldResolveByName() {
    assertEquals(AllocationRule.PRO_RATA, AllocationRule.valueOf("PRO_RATA"));
    assertEquals(AllocationRule.AVG_PRICE, AllocationRule.valueOf("AVG_PRICE"));
    assertEquals(AllocationRule.SEQUENCED, AllocationRule.valueOf("SEQUENCED"));
  }

  @Test
  void shouldRejectInvalidName() {
    assertThrows(IllegalArgumentException.class, () -> AllocationRule.valueOf("INVALID"));
  }

  @Test
  void shouldReturnCorrectName() {
    assertEquals("PRO_RATA", AllocationRule.PRO_RATA.name());
    assertEquals("AVG_PRICE", AllocationRule.AVG_PRICE.name());
    assertEquals("SEQUENCED", AllocationRule.SEQUENCED.name());
  }

  @Test
  void shouldReturnCorrectOrdinal() {
    assertEquals(0, AllocationRule.PRO_RATA.ordinal());
    assertEquals(1, AllocationRule.AVG_PRICE.ordinal());
    assertEquals(2, AllocationRule.SEQUENCED.ordinal());
  }

  @Test
  void shouldReturnSameInstance() {
    assertNotNull(AllocationRule.PRO_RATA);
    assertEquals(AllocationRule.PRO_RATA, AllocationRule.PRO_RATA);
  }
}
