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

class RoundingPolicyTest {

  @Test
  void shouldHaveThreeValues() {
    RoundingPolicy[] values = RoundingPolicy.values();
    assertEquals(3, values.length);
  }

  @Test
  void shouldContainExpectedValues() {
    RoundingPolicy[] values = RoundingPolicy.values();
    assertTrue(java.util.Arrays.asList(values).contains(RoundingPolicy.ROUND_DOWN));
    assertTrue(java.util.Arrays.asList(values).contains(RoundingPolicy.DISTRIBUTE_RESIDUAL));
    assertTrue(java.util.Arrays.asList(values).contains(RoundingPolicy.ROUND_HALF_UP));
  }

  @Test
  void shouldResolveByName() {
    assertEquals(RoundingPolicy.ROUND_DOWN, RoundingPolicy.valueOf("ROUND_DOWN"));
    assertEquals(RoundingPolicy.DISTRIBUTE_RESIDUAL, RoundingPolicy.valueOf("DISTRIBUTE_RESIDUAL"));
    assertEquals(RoundingPolicy.ROUND_HALF_UP, RoundingPolicy.valueOf("ROUND_HALF_UP"));
  }

  @Test
  void shouldRejectInvalidName() {
    assertThrows(IllegalArgumentException.class, () -> RoundingPolicy.valueOf("INVALID"));
  }

  @Test
  void shouldReturnCorrectName() {
    assertEquals("ROUND_DOWN", RoundingPolicy.ROUND_DOWN.name());
    assertEquals("DISTRIBUTE_RESIDUAL", RoundingPolicy.DISTRIBUTE_RESIDUAL.name());
    assertEquals("ROUND_HALF_UP", RoundingPolicy.ROUND_HALF_UP.name());
  }

  @Test
  void shouldReturnCorrectOrdinal() {
    assertEquals(0, RoundingPolicy.ROUND_DOWN.ordinal());
    assertEquals(1, RoundingPolicy.DISTRIBUTE_RESIDUAL.ordinal());
    assertEquals(2, RoundingPolicy.ROUND_HALF_UP.ordinal());
  }

  @Test
  void shouldReturnSameInstance() {
    assertNotNull(RoundingPolicy.ROUND_DOWN);
    assertEquals(RoundingPolicy.ROUND_DOWN, RoundingPolicy.ROUND_DOWN);
  }
}
