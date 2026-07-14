/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AutomationContextTest {

  @Test
  void testRecordComponents() {
    // AutomationContext is a record with order and routes components
    // We test the record structure itself
    assertNotNull(AutomationContext.class.getRecordComponents());
    assertEquals(2, AutomationContext.class.getRecordComponents().length);
    assertEquals("order", AutomationContext.class.getRecordComponents()[0].getName());
    assertEquals("routes", AutomationContext.class.getRecordComponents()[1].getName());
  }

  @Test
  void testConstructorWithOrderOnly() {
    // Test that the convenience constructor exists and works
    // We use null for StagedOrder to avoid construction issues
    AutomationContext ctx = new AutomationContext(null);
    assertNull(ctx.order());
    assertNotNull(ctx.routes());
    assertTrue(ctx.routes().isEmpty());
  }

  @Test
  void testConstructorWithOrderAndRoutes() {
    // Test the full constructor with null StagedOrder
    AutomationContext ctx = new AutomationContext(null, List.of());
    assertNull(ctx.order());
    assertNotNull(ctx.routes());
    assertTrue(ctx.routes().isEmpty());
  }
}
