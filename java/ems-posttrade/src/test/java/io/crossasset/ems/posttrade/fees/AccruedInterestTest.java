/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.fees;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AccruedInterestTest {

  @Test
  void thirty360WithZeroDaysReturnsZero() {
    long accrued = AccruedInterest.thirty360(100000, 425, 0);
    assertEquals(0, accrued);
  }

  @Test
  void actActWithZeroDaysReturnsZero() {
    long accrued = AccruedInterest.actAct(100000, 425, 0, 365);
    assertEquals(0, accrued);
  }

  @Test
  void invalidInputsThrow() {
    assertThrows(IllegalArgumentException.class, () -> AccruedInterest.accrued(-1, 425, 10, 360));
    assertThrows(
        IllegalArgumentException.class, () -> AccruedInterest.accrued(100000, -1, 10, 360));
    assertThrows(
        IllegalArgumentException.class, () -> AccruedInterest.accrued(100000, 425, -1, 360));
    assertThrows(IllegalArgumentException.class, () -> AccruedInterest.accrued(100000, 425, 10, 0));
  }
}
