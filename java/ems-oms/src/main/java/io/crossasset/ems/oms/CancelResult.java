/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

/** Result of {@link StagedOrderManager#cancel(String, long)}. */
public sealed interface CancelResult permits CancelResult.Canceled, CancelResult.Rejected {
  record Canceled(StagedOrder order) implements CancelResult {}

  record Rejected(String orderId, String rejectCode, String message) implements CancelResult {}
}
