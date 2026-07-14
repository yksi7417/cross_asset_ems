/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AutomationEventTest {

  @Test
  void testRecordComponents() {
    assertNotNull(AutomationEvent.class.getRecordComponents());
    assertEquals(3, AutomationEvent.class.getRecordComponents().length);
    assertEquals("eventName", AutomationEvent.class.getRecordComponents()[0].getName());
    assertEquals("orderId", AutomationEvent.class.getRecordComponents()[1].getName());
    assertEquals("properties", AutomationEvent.class.getRecordComponents()[2].getName());
  }

  @Test
  void testConstructorWithEventNameAndOrderId() {
    AutomationEvent event = new AutomationEvent("OrderAccepted", "ORD-123");
    assertEquals("OrderAccepted", event.eventName());
    assertEquals("ORD-123", event.orderId());
    assertNotNull(event.properties());
    assertTrue(event.properties().isEmpty());
  }

  @Test
  void testConstructorWithAllParameters() {
    Map<String, String> props = Map.of("asset_class", "FX", "tif", "DAY");
    AutomationEvent event = new AutomationEvent("OrderAccepted", "ORD-123", props);
    assertEquals("OrderAccepted", event.eventName());
    assertEquals("ORD-123", event.orderId());
    assertEquals(props, event.properties());
  }
}
