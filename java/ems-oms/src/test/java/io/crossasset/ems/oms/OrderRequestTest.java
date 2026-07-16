/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OrderRequestTest {

  @Test
  void testConstruction() {
    OrderRequest req = new OrderRequest("req-1", 1L, "cl-1", "FIGI-1", 1, 100L, 50L, "acct-1", 1);
    assertNotNull(req);
    assertEquals("req-1", req.requestId());
    assertEquals(1L, req.sessionId());
    assertEquals("cl-1", req.clOrdId());
    assertEquals("FIGI-1", req.figi());
    assertEquals(1, req.side());
    assertEquals(100L, req.qty());
    assertEquals(Long.valueOf(50L), req.price());
    assertEquals("acct-1", req.account());
    assertEquals(1, req.tif());
  }

  @Test
  void testNullPriceAllowed() {
    OrderRequest req = new OrderRequest("req-1", 1L, "cl-1", "FIGI-1", 1, 100L, null, "acct-1", 1);
    assertNotNull(req);
    assertEquals(null, req.price());
  }

  @Test
  void testNullRequestIdThrows() {
    assertThrows(
        NullPointerException.class,
        () -> new OrderRequest(null, 1L, "cl-1", "FIGI-1", 1, 100L, null, "acct-1", 1));
  }

  @Test
  void testNullClOrdIdThrows() {
    assertThrows(
        NullPointerException.class,
        () -> new OrderRequest("req", 1L, null, "FIGI-1", 1, 100L, null, "acct-1", 1));
  }

  @Test
  void testNullFigiThrows() {
    assertThrows(
        NullPointerException.class,
        () -> new OrderRequest("req", 1L, "cl-1", null, 1, 100L, null, "acct-1", 1));
  }

  @Test
  void testNullAccountThrows() {
    assertThrows(
        NullPointerException.class,
        () -> new OrderRequest("req", 1L, "cl-1", "FIGI-1", 1, 100L, null, null, 1));
  }
}
