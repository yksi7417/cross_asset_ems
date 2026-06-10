/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory;

import java.util.List;
import java.util.Optional;

/**
 * Submits per-trade reports to the applicable regulators (per
 * arch-regulatory-reporting-service.md). Determines applicable regulators, builds and validates
 * each payload, submits via the per-regulator adapter, and drives the ack/nack/retry lifecycle —
 * each regulator's chain independent of the others. Methods return the events produced, for the
 * event log and STP projection.
 */
public interface RegulatoryReportingService {

  /** Register a per-regulator profile (required fields, deadline, retry, amendment protocol). */
  void registerProfile(ReportingProfile profile);

  /** Register a per-regulator wire adapter. */
  void registerAdapter(RegulatorAdapter adapter);

  /**
   * Report a trade to every applicable regulator. For each: trigger → validate required fields
   * (missing → deferred) → build → submit, retrying nacks up to the profile's limit before failing.
   */
  List<RegReportEvent> report(ReportableTrade trade, String triggeredByEvent);

  /**
   * Report a bust/correct amendment for an existing report: void-and-replace (or amend-in-place per
   * profile) the original and submit the corrected trade.
   */
  List<RegReportEvent> amend(Regulator regulator, ReportableTrade correctedTrade, String reason);

  /** Current state of a report, if known. */
  Optional<ReportState> stateOf(String reportId);
}
