/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.surveillance;

import java.util.List;
import java.util.Objects;

/**
 * A raised surveillance alert (task 12.15, [[arch-surveillance]] § Output —
 * {@code SurveillanceAlertRaised}). Alerts are evidence-citing and deterministic: the same
 * detector version over the same events raises the identical alert (id derived from content,
 * never a random UUID — replay must reproduce it byte-for-byte).
 */
public record Alert(
    String alertId,
    String detectorId,
    int detectorVersion,
    Severity severity,
    String subjectActor,
    String instrumentId,
    List<String> subjectEvents,
    long windowStartMicros,
    long windowEndMicros,
    String rationale) {

  public enum Severity {
    INFO,
    MEDIUM,
    HIGH,
    /** Auto-freeze tier: compliance enforces an actor freeze on subsequent pre-trade gates. */
    CRITICAL
  }

  public Alert {
    Objects.requireNonNull(detectorId, "detectorId");
    Objects.requireNonNull(subjectActor, "subjectActor");
    subjectEvents = List.copyOf(subjectEvents);
  }

  /** Content-derived deterministic alert id. */
  public static Alert of(
      String detectorId,
      int detectorVersion,
      Severity severity,
      String subjectActor,
      String instrumentId,
      List<String> subjectEvents,
      long windowStartMicros,
      long windowEndMicros,
      String rationale) {
    String alertId =
        "ALERT-"
            + detectorId
            + "-v"
            + detectorVersion
            + "-"
            + Integer.toHexString(
                (subjectActor + "|" + instrumentId + "|" + String.join(",", subjectEvents))
                    .hashCode());
    return new Alert(
        alertId,
        detectorId,
        detectorVersion,
        severity,
        subjectActor,
        instrumentId,
        subjectEvents,
        windowStartMicros,
        windowEndMicros,
        rationale);
  }
}
