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

class CftcSdrAdapterTest {

  @Test
  void smokeTest() {
    CftcSdrAdapter adapter = CftcSdrAdapter.acking();
    assertNotNull(adapter);
    assertEquals(Regulator.CFTC_SDR, adapter.regulator());
  }

  @Test
  void buildPayload() {
    CftcSdrAdapter adapter = CftcSdrAdapter.acking();
    ReportableTrade trade =
        new ReportableTrade(
            "T1", "SWAP", "US", "NOTIONAL123", 1, 1000000L, 100L, Map.of("uti", "UTI123"));
    String payload = adapter.buildPayload(trade);
    assertNotNull(payload);
    assertTrue(payload.startsWith("SDR|uti=UTI123"));
    assertTrue(payload.contains("|ref=T1"));
    assertTrue(payload.contains("|assetClass=SWAP"));
    assertTrue(payload.contains("|side=1"));
    assertTrue(payload.contains("|notional=1000000"));
    assertTrue(payload.contains("|px=100"));
  }

  @Test
  void utiFromSupplied() {
    ReportableTrade trade =
        new ReportableTrade(
            "T1", "SWAP", "US", "NOTIONAL123", 1, 1000000L, 100L, Map.of("uti", "UTI123"));
    assertEquals("UTI123", CftcSdrAdapter.uti(trade));
  }

  @Test
  void utiDerived() {
    ReportableTrade trade =
        new ReportableTrade(
            "T1", "SWAP", "US", "NOTIONAL123", 1, 1000000L, 100L, Map.of("reportingLei", "LEI123"));
    String uti = CftcSdrAdapter.uti(trade);
    assertNotNull(uti);
    assertTrue(uti.startsWith("LEI123-"));
  }

  @Test
  void rejectingAdapter() {
    CftcSdrAdapter adapter = CftcSdrAdapter.rejecting();
    assertNotNull(adapter);
    assertEquals(Regulator.CFTC_SDR, adapter.regulator());
  }
}
