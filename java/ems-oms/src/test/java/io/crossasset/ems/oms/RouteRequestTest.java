/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class RouteRequestTest {

  @Test
  void testConstruction() {
    RouteRequest req = new RouteRequest("req-1", "order-1", "XNAS", 100L, 50L, "cl-1");
    assertNotNull(req);
    assertEquals("req-1", req.requestId());
    assertEquals("order-1", req.orderId());
    assertEquals("XNAS", req.venueMic());
    assertEquals(100L, req.qty());
    assertEquals(Long.valueOf(50L), req.price());
    assertEquals("cl-1", req.clOrdId());
  }

  @Test
  void testNullPriceAllowed() {
    RouteRequest req = new RouteRequest("req-1", "order-1", "XNAS", 100L, null, "cl-1");
    assertNotNull(req);
    assertEquals(null, req.price());
  }

  @Test
  void testNullClOrdIdAllowed() {
    RouteRequest req = new RouteRequest("req-1", "order-1", "XNAS", 100L, 50L, null);
    assertNotNull(req);
    assertEquals(null, req.clOrdId());
  }
}
