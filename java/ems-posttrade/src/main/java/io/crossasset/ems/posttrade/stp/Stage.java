/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.stp;

/**
 * The stages of the straight-through-processing pipeline (per arch-stp-pipeline.md). {@link
 * #ALLOCATION} runs first; the remaining stages are independent and fan out from a single {@code
 * AllocationApplied}.
 */
public enum Stage {
  ALLOCATION,
  CONFIRMATION,
  SETTLEMENT_INSTRUCTION,
  REGULATORY_REPORTING,
  BOOKS_AND_RECORDS
}
