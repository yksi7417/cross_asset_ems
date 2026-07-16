/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class RouteResultTest {

  @Test
  void testRejectedRecord() {
    RouteResult.Rejected rej = new RouteResult.Rejected("req-1", "EMS-RTE-2001", "test message");
    assertNotNull(rej);
    assertEquals("req-1", rej.requestId());
    assertEquals("EMS-RTE-2001", rej.rejectCode());
    assertEquals("test message", rej.message());
  }

  @Test
  void testClassExists() {
    assertNotNull(RouteResult.class);
  }
}
