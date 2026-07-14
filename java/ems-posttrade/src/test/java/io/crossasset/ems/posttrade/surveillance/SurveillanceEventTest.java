/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.surveillance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class SurveillanceEventTest {

  @Test
  void eventHoldsFields() {
    SurveillanceEvent event =
        new SurveillanceEvent(
            "event-1",
            SurveillanceEvent.Type.NEW_ORDER,
            "actor-1",
            "instrument-1",
            1,
            100L,
            50000L,
            1000000L);
    assertEquals("event-1", event.eventId());
    assertEquals(SurveillanceEvent.Type.NEW_ORDER, event.type());
    assertEquals("actor-1", event.actor());
    assertEquals("instrument-1", event.instrumentId());
    assertEquals(1, event.side());
    assertEquals(100L, event.qty());
    assertEquals(50000L, event.price());
    assertEquals(1000000L, event.tsMicros());
  }

  @Test
  void typeValues() {
    SurveillanceEvent.Type[] values = SurveillanceEvent.Type.values();
    assertEquals(4, values.length);
    assertNotNull(SurveillanceEvent.Type.NEW_ORDER);
    assertNotNull(SurveillanceEvent.Type.MODIFY);
    assertNotNull(SurveillanceEvent.Type.CANCEL);
    assertNotNull(SurveillanceEvent.Type.EXECUTION);
  }
}
