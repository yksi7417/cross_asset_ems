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

class Rts22AdapterTest {

  @Test
  void smokeTest() {
    Rts22Adapter adapter = Rts22Adapter.acking();
    assertNotNull(adapter);
    assertEquals(Regulator.MIFIR_RTS22, adapter.regulator());
  }

  @Test
  void buildPayload() {
    Rts22Adapter adapter = Rts22Adapter.acking();
    ReportableTrade trade =
        new ReportableTrade(
            "T1",
            "EQUITY",
            "EU",
            "ISIN123",
            1,
            1000L,
            100L,
            Map.of("isin", "ISIN123", "venueMic", "XPAR"));
    String payload = adapter.buildPayload(trade);
    assertNotNull(payload);
    assertTrue(payload.startsWith("RTS22|ref=T1"));
    assertTrue(payload.contains("|isin=ISIN123"));
    assertTrue(payload.contains("|venue=XPAR"));
    assertTrue(payload.contains("|side=1"));
    assertTrue(payload.contains("|qty=1000"));
    assertTrue(payload.contains("|px=100"));
  }

  @Test
  void rejectingAdapter() {
    Rts22Adapter adapter = Rts22Adapter.rejecting();
    assertNotNull(adapter);
    assertEquals(Regulator.MIFIR_RTS22, adapter.regulator());
  }
}
