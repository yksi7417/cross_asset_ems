/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.crossasset.ems.posttrade.regulatory.Regulator;
import io.crossasset.ems.posttrade.regulatory.ReportableTrade;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MsrbRtrsAdapterTest {

  @Test
  void smokeTest() {
    MsrbRtrsAdapter adapter = MsrbRtrsAdapter.acking();
    assertNotNull(adapter);
    assertEquals(Regulator.MSRB_RTRS, adapter.regulator());
  }

  @Test
  void buildPayload() {
    MsrbRtrsAdapter adapter = MsrbRtrsAdapter.acking();
    ReportableTrade trade =
        new ReportableTrade(
            "T1", "MUNICIPAL", "US", "MUNI123", 1, 1000L, 950L, Map.of("cusip", "MUNI123"));
    String payload = adapter.buildPayload(trade);
    assertNotNull(payload);
    assertTrue(payload.startsWith("RTRS|ref=T1"));
    assertTrue(payload.contains("|cusip=MUNI123"));
    assertTrue(payload.contains("|side=1"));
    assertTrue(payload.contains("|par=1000"));
    assertTrue(payload.contains("|px=950"));
  }

  @Test
  void rejectingAdapter() {
    MsrbRtrsAdapter adapter = MsrbRtrsAdapter.rejecting();
    assertNotNull(adapter);
    assertEquals(Regulator.MSRB_RTRS, adapter.regulator());
  }
}
