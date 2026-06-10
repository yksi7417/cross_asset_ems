/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.stp;

import io.crossasset.ems.posttrade.allocation.AllocationEvent;
import io.crossasset.ems.posttrade.allocation.AllocationTemplate;
import io.crossasset.ems.posttrade.allocation.Fill;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates the post-trade STP pipeline for a fill: runs allocation, then fans out to the
 * asset-class profile's downstream stages (confirmation, settlement, reg reporting, books &amp;
 * records). Each stage is independent — an anomaly in one does not block the others (per
 * arch-stp-pipeline.md). The orchestration is event-driven and deterministic; replay reproduces the
 * pipeline byte-identically.
 */
public interface StpOrchestrator {

  /** Result of an ingest/resume: the projected state plus the events produced. */
  record StpResult(StpState state, List<StpEvent> events, List<AllocationEvent> allocationEvents) {}

  /** Register the handler for a downstream stage. Unregistered stages are {@code NOT_REQUIRED}. */
  void register(Stage stage, StageHandler handler);

  /**
   * Ingest a fill and run its pipeline under {@code profile}. Runs allocation first; on {@code
   * AllocationApplied} fans out to the profile's downstream stages.
   */
  StpResult ingest(Fill fill, AllocationTemplate template, StageProfile profile);

  /**
   * Re-run a single stage after the broken artefact was corrected ({@code resume_pipeline_stage}).
   * Other stages are unaffected.
   */
  StpResult resumeStage(String fillId, Stage stage, String by, String reason);

  /** The current projected state for a fill, if the pipeline has been started. */
  Optional<StpState> state(String fillId);
}
