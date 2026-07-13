/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class AggregationRequestTest {

  @Test
  void recordConstruction() {
    AggregationRequest req =
        new AggregationRequest(
            "req-1",
            1000L,
            "cl-1",
            List.of("child-1", "child-2"),
            AllocationRule.PRO_RATA,
            RoundingPolicy.ROUND_DOWN,
            "ACCT");
    assertNotNull(req);
    assertEquals("req-1", req.requestId());
    assertEquals(1000L, req.sessionId());
    assertEquals("cl-1", req.clOrdId());
    assertEquals(List.of("child-1", "child-2"), req.childOrderIds());
    assertEquals(AllocationRule.PRO_RATA, req.rule());
    assertEquals(RoundingPolicy.ROUND_DOWN, req.rounding());
    assertEquals("ACCT", req.account());
  }

  @Test
  void recordWithNullRounding() {
    AggregationRequest req =
        new AggregationRequest(
            "req-1", 1000L, "cl-1", List.of("child-1"), AllocationRule.SEQUENCED, null, "ACCT");
    assertNotNull(req);
    assertEquals(null, req.rounding());
  }

  @Test
  void recordEquality() {
    AggregationRequest r1 =
        new AggregationRequest(
            "req-1", 1000L, "cl-1", List.of("child-1"), AllocationRule.PRO_RATA, null, "ACCT");
    AggregationRequest r2 =
        new AggregationRequest(
            "req-1", 1000L, "cl-1", List.of("child-1"), AllocationRule.PRO_RATA, null, "ACCT");
    assertEquals(r1, r2);
    assertEquals(r1.hashCode(), r2.hashCode());
  }
}
