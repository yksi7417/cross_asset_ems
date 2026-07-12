/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FillTest {

  @Test
  void constructionSetsFields() {
    Fill fill = new Fill("fill-1", "order-1", "route-1", 1000L, 50L);
    assertEquals("fill-1", fill.fillId());
    assertEquals("order-1", fill.orderId());
    assertEquals("route-1", fill.routeId());
    assertEquals(1000L, fill.qty());
    assertEquals(50L, fill.price());
  }

  @Test
  void nullFillIdThrows() {
    assertThrows(NullPointerException.class, () -> new Fill(null, "o", "r", 1L, 1L));
  }

  @Test
  void nullOrderIdThrows() {
    assertThrows(NullPointerException.class, () -> new Fill("f", null, "r", 1L, 1L));
  }

  @Test
  void zeroQtyThrows() {
    assertThrows(IllegalArgumentException.class, () -> new Fill("f", "o", "r", 0L, 1L));
  }

  @Test
  void negativeQtyThrows() {
    assertThrows(IllegalArgumentException.class, () -> new Fill("f", "o", "r", -1L, 1L));
  }

  @Test
  void positiveQtyAllowed() {
    Fill fill = new Fill("f", "o", "r", 1L, 1L);
    assertNotNull(fill);
  }
}
