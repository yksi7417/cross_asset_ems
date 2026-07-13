/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.position;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PositionTest {

  @Test
  void construction() {
    Position pos = new Position("ACCT", "FIGI", 100L, 0L, 100L, 50L, 10L, 20L, 1L);
    assertEquals("ACCT", pos.account());
    assertEquals("FIGI", pos.figi());
    assertEquals(100L, pos.longQty());
    assertEquals(0L, pos.shortQty());
    assertEquals(100L, pos.netQty());
    assertEquals(50L, pos.avgCost());
    assertEquals(10L, pos.realizedPnl());
    assertEquals(20L, pos.unrealizedPnl());
    assertEquals(1L, pos.lastFillEventId());
  }

  @Test
  void nullUnrealizedPnl() {
    Position pos = new Position("ACCT", "FIGI", 0L, 0L, 0L, 0L, 0L, null, 0L);
    assertNull(pos.unrealizedPnl());
  }

  @Test
  void flatPosition() {
    Position pos = Position.flat("ACCT", "FIGI");
    assertEquals("ACCT", pos.account());
    assertEquals("FIGI", pos.figi());
    assertEquals(0L, pos.longQty());
    assertEquals(0L, pos.shortQty());
    assertEquals(0L, pos.netQty());
    assertEquals(0L, pos.avgCost());
    assertEquals(0L, pos.realizedPnl());
    assertNull(pos.unrealizedPnl());
    assertEquals(0L, pos.lastFillEventId());
  }

  @Test
  void rejectsNullAccount() {
    assertThrows(
        NullPointerException.class, () -> new Position(null, "FIGI", 0L, 0L, 0L, 0L, 0L, null, 0L));
  }

  @Test
  void rejectsNullFigi() {
    assertThrows(
        NullPointerException.class, () -> new Position("ACCT", null, 0L, 0L, 0L, 0L, 0L, null, 0L));
  }
}
