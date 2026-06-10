/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.stp;

import java.util.List;

/**
 * The cross-stage events the STP orchestrator emits (per arch-stp-pipeline.md § Cross-stage
 * events). They are event-sourced so the per-trade {@link StpState} is a pure projection and replay
 * reproduces the pipeline byte-identically.
 */
public sealed interface StpEvent {

  String fillId();

  record StpFillIngested(String fillId, String orderId) implements StpEvent {}

  record StpPipelineStarted(String fillId, List<Stage> stagesPlanned) implements StpEvent {}

  record StpStageComplete(String fillId, Stage stage, StageOutcome outcome) implements StpEvent {}

  record StpStageAnomaly(String fillId, Stage stage, String reason, String opsQueue)
      implements StpEvent {}

  record StpPipelineComplete(String fillId, String summary) implements StpEvent {}

  record StpStageRetryRequested(String fillId, Stage stage, String by, String reason)
      implements StpEvent {}
}
