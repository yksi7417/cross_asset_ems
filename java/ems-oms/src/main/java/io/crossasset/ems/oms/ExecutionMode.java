/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

/**
 * Execution semantics for a multi-leg order. Immutable post-stage — switching modes requires cancel
 * + re-stage. Per arch-multileg.md.
 */
public enum ExecutionMode {
  /** All legs trade together or none do; any leg failure voids the package. */
  ALL_OR_NONE,
  /** Legs execute in parallel; partial outcomes accepted. */
  LEGS_INDEPENDENT,
  /** Legs execute in declared order; failure on leg N halts emission of leg N+1. */
  SEQUENCED
}
