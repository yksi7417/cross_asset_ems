/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InMemoryAutomationEngineTest {

  @Test
  void listRules_empty() {
    var engine = new InMemoryAutomationEngine();
    assertNotNull(engine.listRules());
    assertTrue(engine.listRules().isEmpty());
  }
}
