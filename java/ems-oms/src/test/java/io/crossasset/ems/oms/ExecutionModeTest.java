/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ExecutionModeTest {

  @Test
  void testEnumValues() {
    assertNotNull(ExecutionMode.values());
    assertEquals(3, ExecutionMode.values().length);
    assertEquals(ExecutionMode.ALL_OR_NONE, ExecutionMode.valueOf("ALL_OR_NONE"));
    assertEquals(ExecutionMode.LEGS_INDEPENDENT, ExecutionMode.valueOf("LEGS_INDEPENDENT"));
    assertEquals(ExecutionMode.SEQUENCED, ExecutionMode.valueOf("SEQUENCED"));
  }

  @Test
  void testEnumConstants() {
    assertEquals("ALL_OR_NONE", ExecutionMode.ALL_OR_NONE.name());
    assertEquals("LEGS_INDEPENDENT", ExecutionMode.LEGS_INDEPENDENT.name());
    assertEquals("SEQUENCED", ExecutionMode.SEQUENCED.name());
  }
}
