/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory;

import java.util.List;

/**
 * Event-sourced outputs of the regulatory reporting lifecycle (per
 * arch-regulatory-reporting-service.md § Events). Each report tracks its own chain independently of
 * the others triggered by the same trade.
 */
public sealed interface RegReportEvent {

  String reportId();

  record RegReportTriggered(String reportId, String triggeredByEvent, Regulator regulator)
      implements RegReportEvent {}

  record RegReportBuilt(String reportId, String payloadHash) implements RegReportEvent {}

  record RegReportDeferred(String reportId, List<String> missingFields) implements RegReportEvent {}

  record RegReportSubmitted(String reportId, int attempt) implements RegReportEvent {}

  record RegReportAcked(String reportId, String regulatorAckRef) implements RegReportEvent {}

  record RegReportNacked(String reportId, String errorCode, int nextAttempt)
      implements RegReportEvent {}

  record RegReportFailed(String reportId, int afterAttempts) implements RegReportEvent {}

  record RegReportVoided(String reportId, String voidReportId, String reason)
      implements RegReportEvent {}

  record RegReportAmended(String reportId, String amendmentReportId, String reason)
      implements RegReportEvent {}
}
