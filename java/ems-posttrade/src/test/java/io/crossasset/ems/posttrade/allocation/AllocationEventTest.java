/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AllocationEventTest {

  @Test
  void requestedCarriesTemplateAndPolicies() {
    AllocationEvent e =
        new AllocationEvent.AllocationRequested(
            "fill-1",
            "order-1",
            "route-1",
            "tmpl-1",
            3L,
            AllocationPolicy.PRO_RATA,
            RoundingPolicy.DISTRIBUTE_RESIDUAL);

    assertEquals("fill-1", e.fillId());
    var r = (AllocationEvent.AllocationRequested) e;
    assertEquals("tmpl-1", r.templateId());
    assertEquals(3L, r.templateVersion());
    assertEquals(AllocationPolicy.PRO_RATA, r.policy());
    assertEquals(RoundingPolicy.DISTRIBUTE_RESIDUAL, r.rounding());
  }

  @Test
  void appliedCarriesPerAccountAllocation() {
    var e =
        new AllocationEvent.AllocationApplied(
            "alloc-1", "fill-1", "acct-1", "PB-A", 300L, 101_50L, "PB-A");

    assertEquals("fill-1", e.fillId());
    assertEquals("alloc-1", e.allocationId());
    assertEquals("acct-1", e.account());
    assertEquals(300L, e.qty());
    assertEquals(101_50L, e.price());
    assertEquals("PB-A", e.settleTarget());
  }

  @Test
  void deferredKeepsReason() {
    var e = new AllocationEvent.AllocationDeferred("fill-2", "deferred template");
    assertEquals("fill-2", e.fillId());
    assertEquals("deferred template", e.reason());
  }

  @Test
  void reversedPointsAtOriginalAllocation() {
    var e = new AllocationEvent.AllocationReversed("fill-1", "alloc-1", "trade bust");
    assertEquals("fill-1", e.fillId());
    assertEquals("alloc-1", e.originalAllocationId());
    assertEquals("trade bust", e.reason());
  }

  @Test
  void anomalySuggestsAnAction() {
    var e = new AllocationEvent.AllocationAnomaly("fill-3", "weights sum != 10000", "fix template");
    assertEquals("fill-3", e.fillId());
    assertEquals("weights sum != 10000", e.reason());
    assertEquals("fix template", e.suggestedAction());
  }

  @Test
  void sealedHierarchyDispatchesExhaustively() {
    // switch over the sealed interface must cover exactly the five permitted events
    AllocationEvent[] events = {
      new AllocationEvent.AllocationRequested(
          "f", "o", "r", "t", 1L, AllocationPolicy.PRO_RATA, RoundingPolicy.ROUND_DOWN),
      new AllocationEvent.AllocationApplied("a", "f", "acct", "pb", 1L, 1L, "pb"),
      new AllocationEvent.AllocationDeferred("f", "why"),
      new AllocationEvent.AllocationReversed("f", "a", "why"),
      new AllocationEvent.AllocationAnomaly("f", "why", "act")
    };

    for (AllocationEvent e : events) {
      String kind =
          switch (e) {
            case AllocationEvent.AllocationRequested r -> "requested";
            case AllocationEvent.AllocationApplied a -> "applied";
            case AllocationEvent.AllocationDeferred d -> "deferred";
            case AllocationEvent.AllocationReversed r -> "reversed";
            case AllocationEvent.AllocationAnomaly a -> "anomaly";
          };
      assertEquals("f", e.fillId(), kind);
    }
  }
}
