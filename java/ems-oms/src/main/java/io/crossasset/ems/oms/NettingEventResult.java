/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import java.util.List;

/** Result of a fill-booking / cross-booking / un-net operation on an existing netting group. */
public sealed interface NettingEventResult
    permits NettingEventResult.Applied, NettingEventResult.Rejected {

  /**
   * Operation applied. For bookings, {@code allocations} lists each child's share of this event
   * (children receiving zero are omitted); empty for un-net.
   */
  record Applied(NetGroup group, List<ChildAllocation> allocations) implements NettingEventResult {}

  /** Operation rejected; {@code rejectCode} is an EMS-ORD-* catalog code. */
  record Rejected(String groupId, String rejectCode, String message)
      implements NettingEventResult {}
}
