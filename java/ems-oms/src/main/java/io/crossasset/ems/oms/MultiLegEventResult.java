/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

/** Result of a dispatch / cancel / leg-event operation on an existing multi-leg order. */
public sealed interface MultiLegEventResult
    permits MultiLegEventResult.Applied, MultiLegEventResult.Rejected {

  /** Event was applied; {@code order} reflects the updated package state. */
  record Applied(MultiLegOrder order) implements MultiLegEventResult {}

  /** Event was rejected; {@code rejectCode} is an EMS-ORD-* / EMS-RTE-* catalog code. */
  record Rejected(String orderId, String rejectCode, String message)
      implements MultiLegEventResult {}
}
