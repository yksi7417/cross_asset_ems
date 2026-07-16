/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class RouteEventResultTest {

  @Test
  void testRejectedRecord() {
    RouteEventResult.Rejected rej =
        new RouteEventResult.Rejected("route-1", "EMS-RTE-1001", "test message");
    assertNotNull(rej);
    assertEquals("route-1", rej.routeId());
    assertEquals("EMS-RTE-1001", rej.rejectCode());
    assertEquals("test message", rej.message());
  }

  @Test
  void testClassExists() {
    assertNotNull(RouteEventResult.class);
  }
}
