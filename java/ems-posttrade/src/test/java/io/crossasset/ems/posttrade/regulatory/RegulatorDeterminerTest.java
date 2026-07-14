/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class RegulatorDeterminerTest {

  @Test
  void usDefaultsNotNull() {
    RegulatorDeterminer d = RegulatorDeterminer.usDefaults();
    assertNotNull(d);
  }

  @Test
  void crossAssetUsNotNull() {
    RegulatorDeterminer d = RegulatorDeterminer.crossAssetUs();
    assertNotNull(d);
  }

  @Test
  void crossAssetEuNotNull() {
    RegulatorDeterminer d = RegulatorDeterminer.crossAssetEu();
    assertNotNull(d);
  }

  @Test
  void addRuleReturnsThis() {
    RegulatorDeterminer d = new RegulatorDeterminer();
    RegulatorDeterminer result = d.addRule(t -> true, Regulator.TRACE);
    assertSame(d, result);
  }
}
