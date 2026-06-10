/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.allocation;

import java.util.List;

/**
 * Splits route fills into per-account allocations per the order's pinned template (per
 * arch-allocation-service.md). Operates per fill. Every method returns the {@link AllocationEvent}s
 * it produced, in order, so the caller can persist them to the event log and project downstream
 * (position service, STP pipeline, reg reporting).
 *
 * <p>MVP scope: pro-rata splitting with deterministic rounding, pre-allocation validation, the
 * deferred (block-now-allocate-later) workflow, and bust/correct reversal. Multi-PB FIX dispatch
 * and two-level aggregation allocation are post-MVP.
 */
public interface AllocationService {

  /**
   * Allocate a single fill against {@code template}. A deferred template parks the fill and yields
   * an {@link AllocationEvent.AllocationDeferred}; otherwise the fill is split and validated,
   * producing an {@link AllocationEvent.AllocationRequested} followed by one {@link
   * AllocationEvent.AllocationApplied} per account, or an {@link AllocationEvent.AllocationAnomaly}
   * if validation fails.
   */
  List<AllocationEvent> allocate(Fill fill, AllocationTemplate template);

  /**
   * Supply the real template for a previously-deferred order and back-allocate every parked fill.
   * Returns the events for all parked fills (empty if none were parked).
   */
  List<AllocationEvent> setAllocationTemplate(String orderId, AllocationTemplate template);

  /**
   * Reverse every allocation that traced to {@code fillId} (trade bust). Emits one {@link
   * AllocationEvent.AllocationReversed} per prior applied allocation.
   */
  List<AllocationEvent> reverse(String fillId, String reason);

  /**
   * Trade correct: reverse the original allocations for {@code correctedFill.fillId} and re-apply
   * the template against the corrected fill.
   */
  List<AllocationEvent> correct(Fill correctedFill, AllocationTemplate template, String reason);

  /** Fills parked awaiting a deferred template, for the given order. */
  List<Fill> deferredFills(String orderId);

  /** The applied allocations currently on record for a fill (empty after a reversal). */
  List<AllocationEvent.AllocationApplied> appliedFor(String fillId);
}
