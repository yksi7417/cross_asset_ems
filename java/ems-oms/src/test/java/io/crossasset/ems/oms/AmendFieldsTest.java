/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AmendFieldsTest {

  @Test
  void recordConstruction() {
    AmendFields fields = new AmendFields(1000L, 5000L);
    assertNotNull(fields);
    assertEquals(1000L, fields.qty());
    assertEquals(5000L, fields.price());
  }

  @Test
  void isEmptyWhenBothNull() {
    AmendFields fields = new AmendFields(null, null);
    assertTrue(fields.isEmpty());
  }

  @Test
  void notEmptyWhenQtySet() {
    AmendFields fields = new AmendFields(1000L, null);
    assertFalse(fields.isEmpty());
  }

  @Test
  void notEmptyWhenPriceSet() {
    AmendFields fields = new AmendFields(null, 5000L);
    assertFalse(fields.isEmpty());
  }

  @Test
  void recordEquality() {
    AmendFields f1 = new AmendFields(1000L, 5000L);
    AmendFields f2 = new AmendFields(1000L, 5000L);
    assertEquals(f1, f2);
    assertEquals(f1.hashCode(), f2.hashCode());
  }
}
