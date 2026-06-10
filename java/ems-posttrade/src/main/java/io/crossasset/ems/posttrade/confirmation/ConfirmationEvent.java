/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.confirmation;

import java.util.List;

/**
 * Event-sourced outputs of the confirmation / affirmation service (per
 * arch-confirmation-affirmation.md § Events). A {@code ConfirmationMatched} continues the STP
 * pipeline; a {@code ConfirmationDisputed} / {@code AffirmationRejected} queues for ops.
 */
public sealed interface ConfirmationEvent {

  String id();

  // ── Confirmation (trade level) ─────────────────────────────────────────────
  record ConfirmationSubmitted(String id, String tradeRef, String network)
      implements ConfirmationEvent {}

  record ConfirmationMatched(String id) implements ConfirmationEvent {}

  record ConfirmationUnmatched(String id, String reason, List<String> fieldsDiffering)
      implements ConfirmationEvent {}

  record ConfirmationDisputed(String id, String disputedBy, List<String> fields)
      implements ConfirmationEvent {}

  record ConfirmationVoided(String id, String reason, String by) implements ConfirmationEvent {}

  // ── Affirmation (allocation level) ─────────────────────────────────────────
  record AffirmationRequested(String id, String allocationRef, String network)
      implements ConfirmationEvent {}

  record AffirmationReceived(String id) implements ConfirmationEvent {}

  record AffirmationRejected(String id, String reason) implements ConfirmationEvent {}
}
