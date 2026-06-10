/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.allocation;

/**
 * The event-sourced outputs of the allocation pipeline (per arch-allocation-service.md § Allocation
 * events). Every allocation decision is one of these, persisted to the event log; downstream
 * (position service, STP, reg reporting) projects from them.
 */
public sealed interface AllocationEvent
    permits AllocationEvent.AllocationRequested,
        AllocationEvent.AllocationApplied,
        AllocationEvent.AllocationDeferred,
        AllocationEvent.AllocationReversed,
        AllocationEvent.AllocationAnomaly {

  /** The fill this event pertains to. */
  String fillId();

  /** Opens the allocation of a fill; records the template version and policies in effect. */
  record AllocationRequested(
      String fillId,
      String orderId,
      String routeId,
      String templateId,
      long templateVersion,
      AllocationPolicy policy,
      RoundingPolicy rounding)
      implements AllocationEvent {}

  /** One per-account allocation produced from a fill. */
  record AllocationApplied(
      String allocationId,
      String fillId,
      String account,
      String primeBroker,
      long qty,
      long price,
      String settleTarget)
      implements AllocationEvent {}

  /** A fill that could not be allocated yet (deferred template, missing mapping). */
  record AllocationDeferred(String fillId, String reason) implements AllocationEvent {}

  /** Reversal of a prior {@link AllocationApplied} (trade bust / correct / amendment). */
  record AllocationReversed(String fillId, String originalAllocationId, String reason)
      implements AllocationEvent {}

  /** A validation/consistency failure that needs ops triage; the fill stays unallocated. */
  record AllocationAnomaly(String fillId, String reason, String suggestedAction)
      implements AllocationEvent {}
}
