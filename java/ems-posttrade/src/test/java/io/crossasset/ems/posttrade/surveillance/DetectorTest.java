/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.surveillance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class DetectorTest {

  @Test
  void testDetectorImplementation() {
    Detector detector =
        new Detector() {
          @Override
          public String id() {
            return "test-detector";
          }

          @Override
          public int version() {
            return 1;
          }

          @Override
          public long windowMicros() {
            return 60000000L;
          }

          @Override
          public List<Alert> evaluate(List<SurveillanceEvent> window) {
            return List.of();
          }
        };
    assertNotNull(detector);
    assertEquals("test-detector", detector.id());
    assertEquals(1, detector.version());
    assertEquals(60000000L, detector.windowMicros());
  }

  @Test
  void evaluateReturnsEmptyListForEmptyWindow() {
    Detector detector =
        new Detector() {
          @Override
          public String id() {
            return "test-detector";
          }

          @Override
          public int version() {
            return 1;
          }

          @Override
          public long windowMicros() {
            return 60000000L;
          }

          @Override
          public List<Alert> evaluate(List<SurveillanceEvent> window) {
            return List.of();
          }
        };
    List<Alert> alerts = detector.evaluate(List.of());
    assertNotNull(alerts);
    assertEquals(0, alerts.size());
  }
}
