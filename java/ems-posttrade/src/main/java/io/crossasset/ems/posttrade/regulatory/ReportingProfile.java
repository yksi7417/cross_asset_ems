/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory;

import java.util.List;

/**
 * Per-regulator configuration (reference data, versioned with sign-off) — wire format, deadline,
 * required fields, retry policy, and amendment protocol. Per arch-regulatory-reporting-service.md §
 * Per-regulator config.
 */
public record ReportingProfile(
    Regulator regulator,
    String wireFormat,
    long maxLatencyMillis,
    List<String> requiredFields,
    int maxRetries,
    AmendmentProtocol amendmentProtocol) {

  public ReportingProfile {
    requiredFields = List.copyOf(requiredFields);
  }

  /** How a bust/correct is reported. */
  public enum AmendmentProtocol {
    VOID_AND_REPLACE,
    AMEND_IN_PLACE
  }

  /**
   * TRACE for IG corp bonds: FIX TRACE dialect, 15-minute deadline, void-and-replace amendments,
   * and the TRACE required-field set (per arch § Required-field validation).
   */
  public static ReportingProfile trace() {
    return new ReportingProfile(
        Regulator.TRACE,
        "FIX_TRACE",
        15 * 60 * 1000L,
        List.of(
            "trace_party_id",
            "cusip_or_isin",
            "executing_broker",
            "contra_party_id",
            "side",
            "qty",
            "price",
            "yield",
            "trade_date",
            "settle_date"),
        3,
        AmendmentProtocol.VOID_AND_REPLACE);
  }
}
