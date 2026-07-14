/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.confirmation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TradeRecordTest {

  @Test
  void recordFieldsAccessible() {
    TradeRecord record =
        new TradeRecord("t1", "CUSIP1", 1, 100, 1000, 50, "2026-01-01", "2026-01-03", "CP");
    assertEquals("t1", record.tradeRef());
    assertEquals("CUSIP1", record.instrumentId());
    assertEquals(1, record.side());
    assertEquals(100, record.qty());
    assertEquals(1000, record.price());
    assertEquals(50, record.accrued());
    assertEquals("2026-01-01", record.tradeDate());
    assertEquals("2026-01-03", record.settleDate());
    assertEquals("CP", record.counterparty());
  }

  @Test
  void nullTradeRefThrows() {
    assertThrows(
        NullPointerException.class,
        () -> new TradeRecord(null, "CUSIP1", 1, 100, 1000, 0, "2026-01-01", "2026-01-03", "CP"));
  }

  @Test
  void nullInstrumentIdThrows() {
    assertThrows(
        NullPointerException.class,
        () -> new TradeRecord("t1", null, 1, 100, 1000, 0, "2026-01-01", "2026-01-03", "CP"));
  }
}
