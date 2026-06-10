/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

/** Result of {@link StagedOrderManager#amend(String, AmendFields, long)}. */
public sealed interface AmendResult permits AmendResult.Amended, AmendResult.Rejected {
  record Amended(StagedOrder order) implements AmendResult {}

  record Rejected(String orderId, String rejectCode, String message) implements AmendResult {}
}
