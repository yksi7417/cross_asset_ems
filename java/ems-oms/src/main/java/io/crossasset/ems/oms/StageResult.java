/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

/** Result of {@link StagedOrderManager#stage(OrderRequest)}. */
public sealed interface StageResult permits StageResult.Accepted, StageResult.Rejected {
  record Accepted(StagedOrder order) implements StageResult {}

  record Rejected(String requestId, String rejectCode, String message) implements StageResult {}
}
