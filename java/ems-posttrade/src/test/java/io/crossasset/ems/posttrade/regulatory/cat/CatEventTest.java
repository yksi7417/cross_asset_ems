/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory.cat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CatEventTest {

  @Test
  void smokeTest() {
    CatEvent event =
        new CatEvent(
            CatEvent.Type.NEW_ORDER,
            "ORDER123",
            1234567890L,
            Map.of("symbol", "AAPL", "side", "BUY", "qty", "100"));

    assertNotNull(event);
    assertEquals(CatEvent.Type.NEW_ORDER, event.eventType());
    assertEquals("ORDER123", event.orderKey());
    assertEquals(1234567890L, event.eventTimestampMicros());
    assertNotNull(event.fields());
    assertEquals(3, event.fields().size());
    assertEquals("AAPL", event.fields().get("symbol"));
    assertEquals("BUY", event.fields().get("side"));
    assertEquals("100", event.fields().get("qty"));
  }

  @Test
  void toPayload() {
    CatEvent event =
        new CatEvent(
            CatEvent.Type.NEW_ORDER,
            "ORDER123",
            1234567890L,
            Map.of("symbol", "AAPL", "side", "BUY"));

    String payload = event.toPayload();
    assertNotNull(payload);
    assertTrue(payload.startsWith("MENO|orderKey=ORDER123"));
    assertTrue(payload.contains("|ts=1234567890"));
    assertTrue(payload.contains("|symbol=AAPL"));
    assertTrue(payload.contains("|side=BUY"));
  }

  @Test
  void nullFieldsBecomesEmptyMap() {
    CatEvent event = new CatEvent(CatEvent.Type.NEW_ORDER, "ORDER123", 1234567890L, null);
    assertNotNull(event.fields());
    assertTrue(event.fields().isEmpty());
  }

  @Test
  void allEventTypes() {
    assertEquals("MENO", CatEvent.Type.NEW_ORDER.catCode);
    assertEquals("MEOR", CatEvent.Type.ORDER_ROUTE.catCode);
    assertEquals("MEOM", CatEvent.Type.ORDER_MODIFIED.catCode);
    assertEquals("MEOC", CatEvent.Type.ORDER_CANCELED.catCode);
    assertEquals("MEOT", CatEvent.Type.ORDER_TRADE.catCode);
  }
}
