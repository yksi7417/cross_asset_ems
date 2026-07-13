/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AllocationEventTest {

  @Test
  void allocationRequestedFields() {
    AllocationEvent.AllocationRequested ev =
        new AllocationEvent.AllocationRequested(
            "fill-1",
            "order-1",
            "route-1",
            "tmpl-1",
            1L,
            AllocationPolicy.PRO_RATA,
            RoundingPolicy.ROUND_HALF_UP);
    assertEquals("fill-1", ev.fillId());
    assertEquals("order-1", ev.orderId());
    assertEquals("route-1", ev.routeId());
    assertEquals("tmpl-1", ev.templateId());
    assertEquals(1L, ev.templateVersion());
    assertEquals(AllocationPolicy.PRO_RATA, ev.policy());
    assertEquals(RoundingPolicy.ROUND_HALF_UP, ev.rounding());
  }

  @Test
  void allocationAppliedFields() {
    AllocationEvent.AllocationApplied ev =
        new AllocationEvent.AllocationApplied(
            "alloc-1", "fill-1", "acct-1", "pb-1", 100L, 50L, "pb-1");
    assertEquals("alloc-1", ev.allocationId());
    assertEquals("fill-1", ev.fillId());
    assertEquals("acct-1", ev.account());
    assertEquals("pb-1", ev.primeBroker());
    assertEquals(100L, ev.qty());
    assertEquals(50L, ev.price());
    assertEquals("pb-1", ev.settleTarget());
  }

  @Test
  void allocationDeferredFields() {
    AllocationEvent.AllocationDeferred ev =
        new AllocationEvent.AllocationDeferred("fill-1", "missing mapping");
    assertEquals("fill-1", ev.fillId());
    assertEquals("missing mapping", ev.reason());
  }

  @Test
  void allocationReversedFields() {
    AllocationEvent.AllocationReversed ev =
        new AllocationEvent.AllocationReversed("fill-1", "alloc-1", "trade bust");
    assertEquals("fill-1", ev.fillId());
    assertEquals("alloc-1", ev.originalAllocationId());
    assertEquals("trade bust", ev.reason());
  }

  @Test
  void allocationAnomalyFields() {
    AllocationEvent.AllocationAnomaly ev =
        new AllocationEvent.AllocationAnomaly("fill-1", "bad weight", "review template");
    assertEquals("fill-1", ev.fillId());
    assertEquals("bad weight", ev.reason());
    assertEquals("review template", ev.suggestedAction());
  }
}
