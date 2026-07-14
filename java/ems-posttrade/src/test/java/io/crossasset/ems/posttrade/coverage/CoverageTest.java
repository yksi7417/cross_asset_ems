/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.coverage;

import static org.junit.jupiter.api.Assertions.*;

import io.crossasset.ems.instrument.AssetClass;
import org.junit.jupiter.api.Test;

class CoverageTest {

  @Test
  void allCoveragesNotNull() {
    for (Coverage coverage : Coverage.values()) {
      assertNotNull(coverage);
    }
  }

  @Test
  void usIgCorpMapsToFixedIncome() {
    assertEquals(AssetClass.FIXED_INCOME, Coverage.US_IG_CORP.assetClass());
  }

  @Test
  void usEquityMapsToEquity() {
    assertEquals(AssetClass.EQUITY, Coverage.US_EQUITY.assetClass());
  }

  @Test
  void fxSpotMapsToFx() {
    assertEquals(AssetClass.FX, Coverage.FX_SPOT.assetClass());
  }
}
