/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RoundingPolicyTest {

  @Test
  void enumValuesExist() {
    RoundingPolicy[] values = RoundingPolicy.values();
    assertEquals(3, values.length);
  }

  @Test
  void roundDownAccessible() {
    RoundingPolicy policy = RoundingPolicy.ROUND_DOWN;
    assertNotNull(policy);
    assertEquals("ROUND_DOWN", policy.name());
  }

  @Test
  void distributeResidualAccessible() {
    RoundingPolicy policy = RoundingPolicy.DISTRIBUTE_RESIDUAL;
    assertNotNull(policy);
    assertEquals("DISTRIBUTE_RESIDUAL", policy.name());
  }

  @Test
  void roundHalfUpAccessible() {
    RoundingPolicy policy = RoundingPolicy.ROUND_HALF_UP;
    assertNotNull(policy);
    assertEquals("ROUND_HALF_UP", policy.name());
  }
}
