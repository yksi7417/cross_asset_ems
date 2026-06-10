/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.stp;

/** The result a {@link StageHandler} reports for a single pipeline stage. */
public enum StageOutcome {
  /** Stage finished successfully. */
  COMPLETE,
  /** Stage failed and routed to an ops queue; does not block the other stages. */
  ANOMALY,
  /** Stage does not apply to this trade (e.g. TRACE for cash equity). */
  NOT_REQUIRED
}
