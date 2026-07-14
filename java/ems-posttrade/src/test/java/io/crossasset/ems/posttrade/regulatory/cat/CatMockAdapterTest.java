/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory.cat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.crossasset.ems.posttrade.regulatory.Regulator;
import io.crossasset.ems.posttrade.regulatory.ReportableTrade;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatMockAdapterTest {

  @Test
  void smokeTest() {
    CatMockAdapter adapter = CatMockAdapter.acking();
    assertNotNull(adapter);
    assertEquals(Regulator.FINRA_CAT, adapter.regulator());
  }

  @Test
  void buildPayload() {
    CatMockAdapter adapter = CatMockAdapter.acking();
    ReportableTrade trade =
        new ReportableTrade(
            "T1",
            "EQUITY",
            "US",
            "AAPL",
            1,
            100L,
            150L,
            Map.of("orderId", "ORDER123", "eventTs", "1234567890"));

    String payload = adapter.buildPayload(trade);
    assertNotNull(payload);
    assertTrue(payload.startsWith("MEOT|orderKey=ORDER123"));
    assertTrue(payload.contains("|ts=1234567890"));
    assertTrue(payload.contains("|symbol=AAPL"));
    assertTrue(payload.contains("|side=1"));
    assertTrue(payload.contains("|lastQty=100"));
    assertTrue(payload.contains("|lastPx=150"));
    assertTrue(payload.contains("|tradeRef=T1"));
  }

  @Test
  void buildPayloadFallsBackToTradeRef() {
    CatMockAdapter adapter = CatMockAdapter.acking();
    ReportableTrade trade =
        new ReportableTrade("T1", "EQUITY", "US", "AAPL", 1, 100L, 150L, Map.of("eventTs", "0"));

    String payload = adapter.buildPayload(trade);
    assertNotNull(payload);
    assertTrue(payload.contains("|orderKey=T1"));
  }

  @Test
  void rejectingAdapter() {
    CatMockAdapter adapter = CatMockAdapter.rejecting();
    assertNotNull(adapter);
    assertEquals(Regulator.FINRA_CAT, adapter.regulator());
  }
}
