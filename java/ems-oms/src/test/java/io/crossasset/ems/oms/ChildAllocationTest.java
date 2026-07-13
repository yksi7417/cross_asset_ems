/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ChildAllocationTest {

  @Test
  void recordConstruction() {
    ChildAllocation ca = new ChildAllocation("child-1", 100L, 5000L);
    assertNotNull(ca);
    assertEquals("child-1", ca.childOrderId());
    assertEquals(100L, ca.qty());
    assertEquals(5000L, ca.px());
  }

  @Test
  void recordEquality() {
    ChildAllocation ca1 = new ChildAllocation("child-1", 100L, 5000L);
    ChildAllocation ca2 = new ChildAllocation("child-1", 100L, 5000L);
    assertEquals(ca1, ca2);
    assertEquals(ca1.hashCode(), ca2.hashCode());
  }

  @Test
  void recordToString() {
    ChildAllocation ca = new ChildAllocation("child-1", 100L, 5000L);
    String str = ca.toString();
    assertNotNull(str);
    assertEquals("ChildAllocation[childOrderId=child-1, qty=100, px=5000]", str);
  }
}
