/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AggregationGroupTest {

  @Test
  void recordConstruction() {
    Map<String, Long> allocations = Map.of("child-1", 100L, "child-2", 200L);
    AggregationGroup group =
        new AggregationGroup(
            "agg-1",
            List.of("child-1", "child-2"),
            AllocationRule.PRO_RATA,
            RoundingPolicy.ROUND_DOWN,
            allocations,
            300L,
            5000L);
    assertNotNull(group);
    assertEquals("agg-1", group.aggOrderId());
    assertEquals(List.of("child-1", "child-2"), group.childOrderIds());
    assertEquals(AllocationRule.PRO_RATA, group.rule());
    assertEquals(RoundingPolicy.ROUND_DOWN, group.rounding());
    assertEquals(allocations, group.allocations());
    assertEquals(300L, group.totalAllocated());
    assertEquals(5000L, group.avgPx());
  }

  @Test
  void allocatedQtyReturnsZeroForUnknownChild() {
    AggregationGroup group =
        new AggregationGroup(
            "agg-1",
            List.of("child-1"),
            AllocationRule.PRO_RATA,
            null,
            Map.of("child-1", 100L),
            100L,
            0L);
    assertEquals(0L, group.allocatedQty("unknown-child"));
  }

  @Test
  void allocatedQtyReturnsCorrectValue() {
    AggregationGroup group =
        new AggregationGroup(
            "agg-1",
            List.of("child-1"),
            AllocationRule.PRO_RATA,
            null,
            Map.of("child-1", 100L),
            100L,
            0L);
    assertEquals(100L, group.allocatedQty("child-1"));
  }

  @Test
  void recordEquality() {
    Map<String, Long> allocs = Map.of("child-1", 100L);
    AggregationGroup g1 =
        new AggregationGroup(
            "agg-1", List.of("child-1"), AllocationRule.PRO_RATA, null, allocs, 100L, 0L);
    AggregationGroup g2 =
        new AggregationGroup(
            "agg-1", List.of("child-1"), AllocationRule.PRO_RATA, null, allocs, 100L, 0L);
    assertEquals(g1, g2);
    assertEquals(g1.hashCode(), g2.hashCode());
  }
}
