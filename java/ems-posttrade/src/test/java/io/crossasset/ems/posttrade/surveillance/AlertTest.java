/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.surveillance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AlertTest {

  @Test
  void ofCreatesAlertWithDeterministicId() {
    Alert alert =
        Alert.of(
            "detector-1",
            1,
            Alert.Severity.HIGH,
            "actor-1",
            "instrument-1",
            List.of("event-1"),
            1000L,
            2000L,
            "rationale");
    assertNotNull(alert);
    assertEquals("detector-1", alert.detectorId());
    assertEquals(1, alert.detectorVersion());
    assertEquals(Alert.Severity.HIGH, alert.severity());
    assertEquals("actor-1", alert.subjectActor());
    assertEquals("instrument-1", alert.instrumentId());
    assertEquals(List.of("event-1"), alert.subjectEvents());
    assertEquals(1000L, alert.windowStartMicros());
    assertEquals(2000L, alert.windowEndMicros());
    assertEquals("rationale", alert.rationale());
    assertTrue(alert.alertId().startsWith("ALERT-detector-1-v1-"));
  }

  @Test
  void sameInputsProduceSameId() {
    Alert alert1 =
        Alert.of(
            "detector-1",
            1,
            Alert.Severity.HIGH,
            "actor-1",
            "instrument-1",
            List.of("event-1"),
            1000L,
            2000L,
            "rationale");
    Alert alert2 =
        Alert.of(
            "detector-1",
            1,
            Alert.Severity.HIGH,
            "actor-1",
            "instrument-1",
            List.of("event-1"),
            1000L,
            2000L,
            "rationale");
    assertEquals(alert1.alertId(), alert2.alertId());
  }

  @Test
  void severityValues() {
    Alert.Severity[] values = Alert.Severity.values();
    assertEquals(4, values.length);
    assertNotNull(Alert.Severity.INFO);
    assertNotNull(Alert.Severity.MEDIUM);
    assertNotNull(Alert.Severity.HIGH);
    assertNotNull(Alert.Severity.CRITICAL);
  }
}
