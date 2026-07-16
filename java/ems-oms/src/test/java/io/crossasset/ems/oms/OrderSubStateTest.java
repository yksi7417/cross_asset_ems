/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class OrderSubStateTest {

  @Test
  void testValues() {
    OrderSubState[] values = OrderSubState.values();
    assertNotNull(values);
    assertEquals(4, values.length);
    assertEquals(OrderSubState.NEW, values[0]);
    assertEquals(OrderSubState.STAGED, values[1]);
    assertEquals(OrderSubState.READY, values[2]);
    assertEquals(OrderSubState.ROUTING, values[3]);
  }

  @Test
  void testValueOf() {
    assertEquals(OrderSubState.NEW, OrderSubState.valueOf("NEW"));
    assertEquals(OrderSubState.STAGED, OrderSubState.valueOf("STAGED"));
    assertEquals(OrderSubState.READY, OrderSubState.valueOf("READY"));
    assertEquals(OrderSubState.ROUTING, OrderSubState.valueOf("ROUTING"));
  }
}
