/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.stp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class StageOutcomeTest {

  @Test
  void valuesArePresent() {
    StageOutcome[] values = StageOutcome.values();
    assertEquals(3, values.length);
    assertNotNull(StageOutcome.COMPLETE);
    assertNotNull(StageOutcome.ANOMALY);
    assertNotNull(StageOutcome.NOT_REQUIRED);
  }

  @Test
  void nameMatches() {
    assertEquals("COMPLETE", StageOutcome.COMPLETE.name());
    assertEquals("ANOMALY", StageOutcome.ANOMALY.name());
    assertEquals("NOT_REQUIRED", StageOutcome.NOT_REQUIRED.name());
  }
}
