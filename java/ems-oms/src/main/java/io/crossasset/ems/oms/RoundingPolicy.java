/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

/**
 * Where the integer residual of a pro-rata split goes. Every policy conserves the fill: the sum of
 * child allocations always equals the fill quantity. Per arch-aggregation.md.
 */
public enum RoundingPolicy {
  /** Floor each share; the whole residual goes to the last child. */
  ROUND_DOWN,
  /** Floor each share; residual distributed one unit at a time by largest fractional remainder. */
  DISTRIBUTE_RESIDUAL,
  /** Round each share half-up; drift reconciled on the last child. */
  ROUND_HALF_UP
}
