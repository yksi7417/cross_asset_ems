/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.compliance;

/**
 * Compliance verdicts (arch-compliance § Validation vs Compliance): unlike the validator's hard
 * ACCEPT/REJECT, a BLOCK suspends the operation pending a human override, and WARN lets it proceed
 * with an audit flag.
 */
public enum ComplianceOutcome {
  ALLOW,
  BLOCK,
  WARN
}
