/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class LegStateTest {

  @Test
  void allValuesExist() {
    assertNotNull(LegState.PENDING);
    assertNotNull(LegState.ROUTING);
    assertNotNull(LegState.FILLED);
    assertNotNull(LegState.CANCELED);
    assertNotNull(LegState.REJECTED);
  }

  @Test
  void valuesCount() {
    assertEquals(5, LegState.values().length);
  }

  @Test
  void valueOfWorks() {
    assertEquals(LegState.PENDING, LegState.valueOf("PENDING"));
    assertEquals(LegState.REJECTED, LegState.valueOf("REJECTED"));
  }
}
