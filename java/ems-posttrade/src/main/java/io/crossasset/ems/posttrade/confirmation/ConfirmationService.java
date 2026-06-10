/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.confirmation;

import java.util.List;
import java.util.Optional;

/**
 * Matches both sides of a trade electronically so settlement can proceed (per
 * arch-confirmation-affirmation.md). Confirmation is trade-level (price/qty/dates/counterparty);
 * affirmation is allocation-level (the counterparty acks the per-account breakdown). Every method
 * returns the events it produced, for the event log and STP projection.
 */
public interface ConfirmationService {

  /**
   * Post our trade record to {@code network} and match it against the counterparty's. Emits {@code
   * ConfirmationSubmitted} then {@code ConfirmationMatched} or {@code ConfirmationUnmatched}.
   */
  List<ConfirmationEvent> submit(
      String confirmationId,
      TradeRecord ours,
      MatchTolerance tolerance,
      ConfirmationNetwork network);

  /** Route an unmatched/timed-out confirmation to the ops dispute queue. */
  List<ConfirmationEvent> dispute(String confirmationId, String by, List<String> fields);

  /** Mark a disputed confirmation matched after one side corrected its record. */
  List<ConfirmationEvent> resolveMatched(String confirmationId);

  /** Abandon a confirmation (trade voided). */
  List<ConfirmationEvent> voidConfirmation(String confirmationId, String reason, String by);

  /**
   * Post an allocation breakdown for affirmation and capture the counterparty's reply. Emits {@code
   * AffirmationRequested} then {@code AffirmationReceived} or {@code AffirmationRejected}.
   */
  List<ConfirmationEvent> requestAffirmation(
      String affirmationId, String allocationRef, ConfirmationNetwork network);

  /** Current state of a confirmation, if known. */
  Optional<ConfirmationState> stateOf(String confirmationId);
}
