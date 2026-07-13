/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AggregateResultTest {

  @Test
  void rejectedVariantAccessible() {
    AggregateResult.Rejected rejected =
        new AggregateResult.Rejected("req-1", "EMS-ORD-5001", "test rejection");
    assertNotNull(rejected);
    assertEquals("req-1", rejected.requestId());
    assertEquals("EMS-ORD-5001", rejected.rejectCode());
    assertEquals("test rejection", rejected.message());
  }

  @Test
  void rejectedRecordEquality() {
    AggregateResult.Rejected r1 =
        new AggregateResult.Rejected("req-1", "EMS-ORD-5001", "msg");
    AggregateResult.Rejected r2 =
        new AggregateResult.Rejected("req-1", "EMS-ORD-5001", "msg");
    assertEquals(r1, r2);
    assertEquals(r1.hashCode(), r2.hashCode());
  }

  @Test
  void rejectedRecordToString() {
    AggregateResult.Rejected rejected =
        new AggregateResult.Rejected("req-1", "EMS-ORD-5001", "test rejection");
    String str = rejected.toString();
    assertNotNull(str);
    assertEquals(
        "Rejected[requestId=req-1, rejectCode=EMS-ORD-5001, message=test rejection]", str);
  }
}
