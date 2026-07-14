/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class RegulatorTest {

  @Test
  void valuesNotNull() {
    Regulator[] values = Regulator.values();
    assertNotNull(values);
    assertEquals(7, values.length);
  }

  @Test
  void valueOfWorks() {
    assertEquals(Regulator.TRACE, Regulator.valueOf("TRACE"));
    assertEquals(Regulator.CFTC_SDR, Regulator.valueOf("CFTC_SDR"));
    assertEquals(Regulator.UK_EMIR_TR, Regulator.valueOf("UK_EMIR_TR"));
  }
}
