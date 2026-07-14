/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MockRegulatorAdapterTest {

  @Test
  void ackingReturnsAdapterForRegulator() {
    MockRegulatorAdapter adapter = MockRegulatorAdapter.acking(Regulator.TRACE);
    assertEquals(Regulator.TRACE, adapter.regulator());
  }

  @Test
  void rejectingReturnsAdapterForRegulator() {
    MockRegulatorAdapter adapter = MockRegulatorAdapter.rejecting(Regulator.CFTC_SDR);
    assertEquals(Regulator.CFTC_SDR, adapter.regulator());
  }
}
