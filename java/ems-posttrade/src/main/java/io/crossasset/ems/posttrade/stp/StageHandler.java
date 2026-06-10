/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.stp;

import io.crossasset.ems.posttrade.allocation.AllocationEvent.AllocationApplied;
import java.util.List;

/**
 * A pluggable downstream pipeline stage. The orchestrator invokes one per planned stage after
 * allocation succeeds, passing the per-account allocations the stage operates on. Real
 * implementations (confirmation in 12.3, TRACE-mock reg reporting in 12.6, settlement, B&R)
 * register against a {@link Stage}; an unregistered stage defaults to {@link
 * StageOutcome#NOT_REQUIRED}.
 *
 * <p>Handlers must be deterministic and side-effect-sandboxed under replay (arch-stp-pipeline.md §
 * Determinism) — outbound calls to PB / regulator are mocked in replay mode.
 */
@FunctionalInterface
public interface StageHandler {

  /** Context handed to a stage: the fill and the allocations produced for it. */
  record StageContext(String fillId, String orderId, List<AllocationApplied> allocations) {}

  StageOutcome handle(StageContext context);
}
