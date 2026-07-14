/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.confirmation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MatchToleranceTest {

  @Test
  void exactHasZeroTolerances() {
    MatchTolerance t = MatchTolerance.exact();
    assertEquals(0, t.priceTolerance());
    assertEquals(0, t.qtyTolerance());
    assertEquals(0, t.accruedTolerance());
  }

  @Test
  void corpBondHasHalfTickAndAccrued() {
    MatchTolerance t = MatchTolerance.corpBond(5, 10);
    assertEquals(5, t.priceTolerance());
    assertEquals(0, t.qtyTolerance());
    assertEquals(10, t.accruedTolerance());
  }

  @Test
  void fxHasPips() {
    MatchTolerance t = MatchTolerance.fx(2);
    assertEquals(2, t.priceTolerance());
    assertEquals(0, t.qtyTolerance());
    assertEquals(0, t.accruedTolerance());
  }
}
