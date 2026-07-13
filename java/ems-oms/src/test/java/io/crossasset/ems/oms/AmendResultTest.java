/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class AmendResultTest {

  @Test
  void rejectedVariantAccessible() {
    AmendResult.Rejected rejected =
        new AmendResult.Rejected("ord-1", "EMS-ORD-5003", "test rejection");
    assertNotNull(rejected);
    assertEquals("ord-1", rejected.orderId());
    assertEquals("EMS-ORD-5003", rejected.rejectCode());
    assertEquals("test rejection", rejected.message());
  }

  @Test
  void rejectedRecordEquality() {
    AmendResult.Rejected r1 = new AmendResult.Rejected("ord-1", "EMS-ORD-5003", "msg");
    AmendResult.Rejected r2 = new AmendResult.Rejected("ord-1", "EMS-ORD-5003", "msg");
    assertEquals(r1, r2);
    assertEquals(r1.hashCode(), r2.hashCode());
  }

  @Test
  void rejectedRecordToString() {
    AmendResult.Rejected rejected =
        new AmendResult.Rejected("ord-1", "EMS-ORD-5003", "test rejection");
    String str = rejected.toString();
    assertNotNull(str);
    assertEquals("Rejected[orderId=ord-1, rejectCode=EMS-ORD-5003, message=test rejection]", str);
  }
}
