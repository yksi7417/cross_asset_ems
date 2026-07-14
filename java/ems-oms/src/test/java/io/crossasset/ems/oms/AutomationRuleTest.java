/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AutomationRuleTest {

  @Test
  void testRecordComponents() {
    assertNotNull(AutomationRule.class.getRecordComponents());
    assertEquals(8, AutomationRule.class.getRecordComponents().length);
    assertEquals("ruleId", AutomationRule.class.getRecordComponents()[0].getName());
    assertEquals("scope", AutomationRule.class.getRecordComponents()[1].getName());
    assertEquals("scopeRef", AutomationRule.class.getRecordComponents()[2].getName());
    assertEquals("triggerEvent", AutomationRule.class.getRecordComponents()[3].getName());
    assertEquals("condition", AutomationRule.class.getRecordComponents()[4].getName());
    assertEquals("actions", AutomationRule.class.getRecordComponents()[5].getName());
    assertEquals("priority", AutomationRule.class.getRecordComponents()[6].getName());
    assertEquals("enabled", AutomationRule.class.getRecordComponents()[7].getName());
  }

  @Test
  void testConstructorWithCondition() {
    AutomationRule rule =
        new AutomationRule(
            "RULE-1",
            AutomationScope.FIRM,
            null,
            "OrderAccepted",
            ctx -> true,
            List.of(),
            100,
            true);
    assertEquals("RULE-1", rule.ruleId());
    assertEquals(AutomationScope.FIRM, rule.scope());
    assertEquals(null, rule.scopeRef());
    assertEquals("OrderAccepted", rule.triggerEvent());
    assertNotNull(rule.condition());
    assertTrue(rule.condition().test(null)); // Should accept null context
    assertTrue(rule.actions().isEmpty());
    assertEquals(100, rule.priority());
    assertTrue(rule.enabled());
  }

  @Test
  void testConstructorWithoutCondition() {
    AutomationRule rule =
        new AutomationRule(
            "RULE-2", AutomationScope.DESK, "DESK-1", "OrderReplaced", List.of(), 50, false);
    assertEquals("RULE-2", rule.ruleId());
    assertEquals(AutomationScope.DESK, rule.scope());
    assertEquals("DESK-1", rule.scopeRef());
    assertEquals("OrderReplaced", rule.triggerEvent());
    assertNotNull(rule.condition());
    assertTrue(rule.condition().test(null)); // Convenience constructor uses always-true
    assertTrue(rule.actions().isEmpty());
    assertEquals(50, rule.priority());
    assertTrue(!rule.enabled());
  }
}
