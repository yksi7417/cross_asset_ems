/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class AutomationScopeTest {

  @Test
  void testEnumValues() {
    assertNotNull(AutomationScope.values());
    assertEquals(4, AutomationScope.values().length);
    assertEquals(AutomationScope.FIRM, AutomationScope.valueOf("FIRM"));
    assertEquals(AutomationScope.DESK, AutomationScope.valueOf("DESK"));
    assertEquals(AutomationScope.USER, AutomationScope.valueOf("USER"));
    assertEquals(AutomationScope.TAG, AutomationScope.valueOf("TAG"));
  }

  @Test
  void testEnumConstants() {
    assertEquals("FIRM", AutomationScope.FIRM.name());
    assertEquals("DESK", AutomationScope.DESK.name());
    assertEquals("USER", AutomationScope.USER.name());
    assertEquals("TAG", AutomationScope.TAG.name());
  }
}
