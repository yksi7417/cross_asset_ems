/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ReportStateTest {

  @Test
  void valuesNotNull() {
    ReportState[] values = ReportState.values();
    assertNotNull(values);
    assertEquals(10, values.length);
  }

  @Test
  void valueOfWorks() {
    assertEquals(ReportState.TRIGGERED, ReportState.valueOf("TRIGGERED"));
    assertEquals(ReportState.FAILED, ReportState.valueOf("FAILED"));
    assertEquals(ReportState.VOIDED, ReportState.valueOf("VOIDED"));
  }
}
