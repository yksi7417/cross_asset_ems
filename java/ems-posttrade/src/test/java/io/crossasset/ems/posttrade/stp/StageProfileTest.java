/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.stp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class StageProfileTest {

  @Test
  void corpBondHasExpectedStages() {
    StageProfile profile = StageProfile.corpBond();
    assertNotNull(profile);
    assertEquals("CORP_BOND", profile.assetClass());
    List<Stage> stages = profile.downstreamStages();
    assertEquals(4, stages.size());
    assertEquals(Stage.CONFIRMATION, stages.get(0));
    assertEquals(Stage.SETTLEMENT_INSTRUCTION, stages.get(1));
    assertEquals(Stage.REGULATORY_REPORTING, stages.get(2));
    assertEquals(Stage.BOOKS_AND_RECORDS, stages.get(3));
  }

  @Test
  void cashEquityHasExpectedStages() {
    StageProfile profile = StageProfile.cashEquity();
    assertNotNull(profile);
    assertEquals("CASH_EQUITY", profile.assetClass());
    List<Stage> stages = profile.downstreamStages();
    assertEquals(2, stages.size());
    assertEquals(Stage.SETTLEMENT_INSTRUCTION, stages.get(0));
    assertEquals(Stage.BOOKS_AND_RECORDS, stages.get(1));
  }

  @Test
  void fxSpotHasExpectedStages() {
    StageProfile profile = StageProfile.fxSpot();
    assertNotNull(profile);
    assertEquals("FX_SPOT", profile.assetClass());
    List<Stage> stages = profile.downstreamStages();
    assertEquals(3, stages.size());
    assertEquals(Stage.CONFIRMATION, stages.get(0));
    assertEquals(Stage.SETTLEMENT_INSTRUCTION, stages.get(1));
    assertEquals(Stage.BOOKS_AND_RECORDS, stages.get(2));
  }
}
