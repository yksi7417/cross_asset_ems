/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import java.util.List;

/** Result of an allocate-fill / unaggregate operation on an existing aggregation group. */
public sealed interface AggregationEventResult
    permits AggregationEventResult.Applied, AggregationEventResult.Rejected {

  /**
   * Operation applied. For fills, {@code allocations} lists each child's share of this fill
   * (children receiving zero are omitted); empty for unaggregate.
   */
  record Applied(AggregationGroup group, List<ChildAllocation> allocations)
      implements AggregationEventResult {}

  /** Operation rejected; {@code rejectCode} is an EMS-ORD-* catalog code. */
  record Rejected(String aggOrderId, String rejectCode, String message)
      implements AggregationEventResult {}
}
