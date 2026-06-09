/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa;

/**
 * Result of a logon attempt. Per entry-point-aaa.md.
 *
 * <p>Task 5.1 — AAA service skeleton.
 */
public sealed interface LogonOutcome permits LogonOutcome.Accepted, LogonOutcome.Rejected {

  /** Logon succeeded; session is established. */
  record Accepted(Session session) implements LogonOutcome {}

  /** Logon failed; rejectCode is one of the EMS-SES-* catalog codes. */
  record Rejected(String rejectCode, String message) implements LogonOutcome {}
}
