/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

/**
 * How block fills are distributed back to the aggregated children. Per arch-aggregation.md.
 * CUSTOM(rule_id) — an automation-layer rule computing allocations — is deferred until the rule
 * binding lands (arch-automation-layer).
 */
public enum AllocationRule {
  /** Allocate by weight of original child qty; residual handled per {@link RoundingPolicy}. */
  PRO_RATA,
  /** Pro-rata quantities; every child carries the running average fill price. */
  AVG_PRICE,
  /** Fills satisfy children in declared order until each child's qty is met. */
  SEQUENCED
}
