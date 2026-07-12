/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class RoundingPolicyTest {

  @Test
  void valuesExist() {
    RoundingPolicy[] values = RoundingPolicy.values();
    assertNotNull(values);
    assertEquals(4, values.length);
  }

  @Test
  void roundHalfUpExists() {
    assertEquals(RoundingPolicy.ROUND_HALF_UP, RoundingPolicy.valueOf("ROUND_HALF_UP"));
  }

  @Test
  void roundDownExists() {
    assertEquals(RoundingPolicy.ROUND_DOWN, RoundingPolicy.valueOf("ROUND_DOWN"));
  }

  @Test
  void distributeResidualExists() {
    assertEquals(RoundingPolicy.DISTRIBUTE_RESIDUAL, RoundingPolicy.valueOf("DISTRIBUTE_RESIDUAL"));
  }

  @Test
  void largestShareFirstExists() {
    assertEquals(RoundingPolicy.LARGEST_SHARE_FIRST, RoundingPolicy.valueOf("LARGEST_SHARE_FIRST"));
  }
}
