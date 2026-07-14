/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.fees;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FeeScheduleTest {

  @Test
  void bpsFactory() {
    FeeSchedule fs = FeeSchedule.bps(10);
    assertEquals(10, fs.commissionBps());
    assertEquals(0, fs.perUnitFee());
    assertEquals(0, fs.minCommission());
    assertEquals(0, fs.maxCommission());
    assertEquals(0, fs.regulatoryFeeBps());
    assertFalse(fs.regFeeSellOnly());
  }

  @Test
  void perShareFactory() {
    FeeSchedule fs = FeeSchedule.perShare(5, 0);
    assertEquals(0, fs.commissionBps());
    assertEquals(5, fs.perUnitFee());
    assertEquals(0, fs.minCommission());
    assertEquals(0, fs.maxCommission());
    assertEquals(0, fs.regulatoryFeeBps());
    assertTrue(fs.regFeeSellOnly());
  }

  @Test
  void directConstruction() {
    FeeSchedule fs = new FeeSchedule(10, 5, 1, 20, 0, false);
    assertEquals(10, fs.commissionBps());
    assertEquals(5, fs.perUnitFee());
    assertEquals(1, fs.minCommission());
    assertEquals(20, fs.maxCommission());
    assertEquals(0, fs.regulatoryFeeBps());
    assertFalse(fs.regFeeSellOnly());
  }

  @Test
  void validationRejectsNegativeCommissionBps() {
    assertThrows(IllegalArgumentException.class, () -> new FeeSchedule(-1, 0, 0, 0, 0, false));
  }

  @Test
  void validationRejectsNegativePerUnitFee() {
    assertThrows(IllegalArgumentException.class, () -> new FeeSchedule(0, -1, 0, 0, 0, false));
  }

  @Test
  void validationRejectsNegativeRegulatoryFeeBps() {
    assertThrows(IllegalArgumentException.class, () -> new FeeSchedule(0, 0, 0, 0, -1, false));
  }

  @Test
  void validationRejectsMaxLessThanMin() {
    assertThrows(IllegalArgumentException.class, () -> new FeeSchedule(0, 0, 20, 10, 0, false));
  }
}
