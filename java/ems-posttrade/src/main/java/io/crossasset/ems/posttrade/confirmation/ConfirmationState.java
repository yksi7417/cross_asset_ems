/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.confirmation;

/**
 * Confirmation lifecycle states (per arch-confirmation-affirmation.md § State machine): {@code
 * Pending → Submitted → Matched | Unmatched → Disputed → Matched | Voided}, with a {@code TimedOut}
 * path when no counterparty posts within the window.
 */
public enum ConfirmationState {
  PENDING,
  SUBMITTED,
  MATCHED,
  UNMATCHED,
  DISPUTED,
  TIMED_OUT,
  VOIDED
}
