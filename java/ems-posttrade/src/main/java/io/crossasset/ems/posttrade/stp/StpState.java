/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.stp;

import java.util.Map;

/**
 * The per-trade pipeline status (per arch-stp-pipeline.md § Per-trade pipeline state). A pure
 * projection of the {@link StpEvent} stream, surfaced on the ops dashboard.
 */
public record StpState(
    String fillId,
    String orderId,
    AllocationState allocation,
    Map<Stage, StageOutcome> stages,
    Overall overall) {

  public StpState {
    stages = Map.copyOf(stages);
  }

  /** Allocation-stage status. */
  public enum AllocationState {
    PENDING,
    APPLIED,
    DEFERRED,
    ANOMALY
  }

  /** Roll-up of the whole pipeline. */
  public enum Overall {
    IN_PROGRESS,
    COMPLETE,
    ANOMALY
  }
}
