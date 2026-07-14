/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class LegRequestTest {

  @Test
  void constructWithAllParams() {
    LegRequest leg = new LegRequest(1, "FIGI-001", 1, 1000L, 50_000L, "XNYS");
    assertNotNull(leg);
    assertEquals(1, leg.legRatio());
    assertEquals("FIGI-001", leg.figi());
    assertEquals(1, leg.side());
    assertEquals(1000L, leg.qty());
    assertEquals(Long.valueOf(50_000L), leg.price());
    assertEquals("XNYS", leg.venueMic());
  }

  @Test
  void constructWithNullPrice() {
    LegRequest leg = new LegRequest(-1, "FIGI-002", 2, 500L, null, "XLON");
    assertNotNull(leg);
    assertEquals(-1, leg.legRatio());
    assertEquals("FIGI-002", leg.figi());
    assertEquals(2, leg.side());
    assertEquals(500L, leg.qty());
    assertNull(leg.price());
    assertEquals("XLON", leg.venueMic());
  }
}
