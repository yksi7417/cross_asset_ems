/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.allocation;

/**
 * How the residual lots left by integer division of a fill are distributed. Per
 * arch-allocation-service.md. All policies are deterministic so replay reproduces identical splits.
 *
 * <ul>
 *   <li>{@link #DISTRIBUTE_RESIDUAL} — largest-remainder method: residual lots go to the accounts
 *       with the largest fractional remainder (tie-break: larger weight, then lower index).
 *   <li>{@link #ROUND_HALF_UP} — same largest-remainder distribution (nearest with bias up).
 *   <li>{@link #LARGEST_SHARE_FIRST} — residual goes to the highest-weight account(s) first.
 *   <li>{@link #ROUND_DOWN} — floor every account; any residual lots stay unallocated.
 * </ul>
 */
public enum RoundingPolicy {
  ROUND_HALF_UP,
  ROUND_DOWN,
  DISTRIBUTE_RESIDUAL,
  LARGEST_SHARE_FIRST
}
