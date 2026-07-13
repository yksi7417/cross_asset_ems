/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AutomationActionTest {

  @Test
  void routeOrdersWithPrice() {
    AutomationAction.RouteOrders action =
        new AutomationAction.RouteOrders("VENUE-1", 5000L);
    assertNotNull(action);
    assertEquals("VENUE-1", action.venueMic());
    assertEquals(5000L, action.price());
  }

  @Test
  void routeOrdersWithoutPrice() {
    AutomationAction.RouteOrders action = new AutomationAction.RouteOrders("VENUE-1");
    assertNotNull(action);
    assertEquals("VENUE-1", action.venueMic());
    assertEquals(null, action.price());
  }

  @Test
  void cancelRouteAccessible() {
    AutomationAction.CancelRoute action = new AutomationAction.CancelRoute("route-1");
    assertNotNull(action);
    assertEquals("route-1", action.routeId());
  }

  @Test
  void setPendingActionDoneAccessible() {
    AutomationAction.SetPendingActionDone action =
        new AutomationAction.SetPendingActionDone("key-1");
    assertNotNull(action);
    assertEquals("key-1", action.pendingActionKey());
  }

  @Test
  void markOrderReadyAccessible() {
    AutomationAction.MarkOrderReady action = new AutomationAction.MarkOrderReady();
    assertNotNull(action);
  }

  @Test
  void routeOrdersRecordEquality() {
    AutomationAction.RouteOrders r1 =
        new AutomationAction.RouteOrders("VENUE-1", 5000L);
    AutomationAction.RouteOrders r2 =
        new AutomationAction.RouteOrders("VENUE-1", 5000L);
    assertEquals(r1, r2);
    assertEquals(r1.hashCode(), r2.hashCode());
  }
}
