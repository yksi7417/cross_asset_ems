/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import java.util.Optional;

/**
 * Combines N staged orders into one execution-facing block order and allocates fills back to the
 * children. The block parent is a real {@link StagedOrder}, so the router routes it unchanged;
 * fills landing on the parent are pushed back through {@link #allocateFill} and propagate to each
 * child's order FSM.
 *
 * <p>Aggregation is trader- or rule-initiated — the EMS never aggregates silently. Eligibility per
 * arch-aggregation.md: same instrument (exact FIGI), same side, compatible TIF, child READY and
 * unfilled, child not already in an active group, all children on the caller's session (cross-desk
 * aggregation requires the #cross-desk-aggregator tag — not yet wired, rejected with EMS-PRM-1601).
 *
 * <p>Per arch-aggregation.md, task 7.5. Out of scope for v1: extend_aggregation (venue-capability
 * dependent, task 11.x), CUSTOM allocation rules (needs automation-layer rule binding), and
 * SOM-level freeze enforcement (children canceled directly in the SOM while aggregated are caught
 * at allocation time; hard freeze arrives with the event-sourced projection).
 */
public interface AggregationManager {

  /**
   * Validates eligibility (EMS-ORD-5001 with the failing predicate named, EMS-ORD-5002 missing
   * rounding, EMS-PRM-1601 cross-desk, EMS-ORD-4001 unknown child), stages the block parent (qty =
   * Σ children, most conservative child limit price), marks it READY, and freezes the children into
   * the group.
   */
  AggregateResult aggregate(AggregationRequest request);

  /**
   * Allocates one block fill back to the children per the group's {@link AllocationRule} and {@link
   * RoundingPolicy}. Conserves the fill exactly; each child's cumulative allocation is capped at
   * its original qty (EMS-ORD-3003 when the fill exceeds the group remainder). Child order FSMs
   * advance via PartialFill / FullFill.
   */
  AggregationEventResult allocateFill(String aggOrderId, long fillQty, long fillPx);

  /**
   * Dissolves a group with no fills yet (EMS-ORD-3003 otherwise): cancels the block parent and
   * frees the children for re-aggregation.
   */
  AggregationEventResult unaggregate(String aggOrderId, long sessionId);

  /** Returns the group if it exists, empty otherwise. */
  Optional<AggregationGroup> findGroup(String aggOrderId);
}
