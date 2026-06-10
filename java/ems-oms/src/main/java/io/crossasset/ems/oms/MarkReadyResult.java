/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

/** Result of {@link StagedOrderManager#markReady(String, long)}. */
public sealed interface MarkReadyResult permits MarkReadyResult.Ready, MarkReadyResult.Rejected {
  record Ready(StagedOrder order) implements MarkReadyResult {}

  record Rejected(String orderId, String rejectCode, String message) implements MarkReadyResult {}
}
