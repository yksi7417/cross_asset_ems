/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

/**
 * Result of {@link MultiLegOrderManager#stage(MultiLegOrderRequest)}.
 *
 * <p>Rejected packages are still persisted in REJECTED state for the audit trail (the MultiLeg FSM
 * models STAGED → REJECTED on LegsValidationFailed); {@code orderId} locates them.
 */
public sealed interface MultiLegStageResult
    permits MultiLegStageResult.Staged, MultiLegStageResult.Rejected {

  /** Package validated; order is in READY state. */
  record Staged(MultiLegOrder order) implements MultiLegStageResult {}

  /** Package failed validation; persisted in REJECTED state under {@code orderId}. */
  record Rejected(String requestId, String orderId, String rejectCode, String message)
      implements MultiLegStageResult {}
}
