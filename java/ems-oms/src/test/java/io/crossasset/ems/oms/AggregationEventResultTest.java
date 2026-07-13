/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class AggregationEventResultTest {

  @Test
  void rejectedVariantAccessible() {
    AggregationEventResult.Rejected rejected =
        new AggregationEventResult.Rejected("agg-1", "EMS-ORD-5002", "test rejection");
    assertNotNull(rejected);
    assertEquals("agg-1", rejected.aggOrderId());
    assertEquals("EMS-ORD-5002", rejected.rejectCode());
    assertEquals("test rejection", rejected.message());
  }

  @Test
  void rejectedRecordEquality() {
    AggregationEventResult.Rejected r1 =
        new AggregationEventResult.Rejected("agg-1", "EMS-ORD-5002", "msg");
    AggregationEventResult.Rejected r2 =
        new AggregationEventResult.Rejected("agg-1", "EMS-ORD-5002", "msg");
    assertEquals(r1, r2);
    assertEquals(r1.hashCode(), r2.hashCode());
  }

  @Test
  void rejectedRecordToString() {
    AggregationEventResult.Rejected rejected =
        new AggregationEventResult.Rejected("agg-1", "EMS-ORD-5002", "test rejection");
    String str = rejected.toString();
    assertNotNull(str);
    assertEquals(
        "Rejected[aggOrderId=agg-1, rejectCode=EMS-ORD-5002, message=test rejection]", str);
  }

  @Test
  void appliedVariantAccessible() {
    AggregationGroup group =
        new AggregationGroup(
            "agg-1",
            List.of("child-1"),
            AllocationRule.PRO_RATA,
            null,
            List.of("child-1").stream()
                .collect(java.util.stream.Collectors.toMap(id -> id, id -> 0L)),
            0L,
            0L);
    List<ChildAllocation> allocations = List.of(new ChildAllocation("child-1", 100L, 5000L));
    AggregationEventResult.Applied applied = new AggregationEventResult.Applied(group, allocations);
    assertNotNull(applied);
    assertEquals(group, applied.group());
    assertEquals(allocations, applied.allocations());
  }
}
