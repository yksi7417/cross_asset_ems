/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportableTradeTest {

  @Test
  void smokeTest() {
    ReportableTrade trade =
        new ReportableTrade(
            "T1",
            "FIXED_INCOME",
            "US",
            "CUSIP123",
            1,
            1000L,
            950L,
            Map.of("cusip", "CUSIP123", "yield", "4.5"));

    assertNotNull(trade);
    assertEquals("T1", trade.tradeRef());
    assertEquals("FIXED_INCOME", trade.assetClass());
    assertEquals("US", trade.jurisdiction());
    assertEquals("CUSIP123", trade.instrumentId());
    assertEquals(1, trade.side());
    assertEquals(1000L, trade.qty());
    assertEquals(950L, trade.price());
    assertNotNull(trade.fields());
    assertEquals(2, trade.fields().size());
    assertEquals("CUSIP123", trade.fields().get("cusip"));
    assertEquals("4.5", trade.fields().get("yield"));
  }

  @Test
  void hasFieldPresent() {
    ReportableTrade trade =
        new ReportableTrade(
            "T1", "FIXED_INCOME", "US", "CUSIP123", 1, 1000L, 950L, Map.of("cusip", "CUSIP123"));
    assertTrue(trade.hasField("cusip"));
  }

  @Test
  void hasFieldMissing() {
    ReportableTrade trade =
        new ReportableTrade(
            "T1", "FIXED_INCOME", "US", "CUSIP123", 1, 1000L, 950L, Map.of("cusip", "CUSIP123"));
    assertFalse(trade.hasField("missing"));
  }

  @Test
  void hasFieldBlank() {
    ReportableTrade trade =
        new ReportableTrade(
            "T1", "FIXED_INCOME", "US", "CUSIP123", 1, 1000L, 950L, Map.of("cusip", "  "));
    assertFalse(trade.hasField("cusip"));
  }

  @Test
  void nullFieldsBecomesEmptyMap() {
    ReportableTrade trade =
        new ReportableTrade("T1", "FIXED_INCOME", "US", "CUSIP123", 1, 1000L, 950L, null);
    assertNotNull(trade.fields());
    assertTrue(trade.fields().isEmpty());
  }
}
