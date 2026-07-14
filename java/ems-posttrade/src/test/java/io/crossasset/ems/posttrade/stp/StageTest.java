/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.stp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class StageTest {

  @Test
  void smokeTest() {
    assertNotNull(Stage.ALLOCATION);
    assertNotNull(Stage.CONFIRMATION);
    assertNotNull(Stage.SETTLEMENT_INSTRUCTION);
    assertNotNull(Stage.REGULATORY_REPORTING);
    assertNotNull(Stage.BOOKS_AND_RECORDS);
  }

  @Test
  void values() {
    Stage[] stages = Stage.values();
    assertEquals(5, stages.length);
    assertEquals(Stage.ALLOCATION, stages[0]);
    assertEquals(Stage.CONFIRMATION, stages[1]);
    assertEquals(Stage.SETTLEMENT_INSTRUCTION, stages[2]);
    assertEquals(Stage.REGULATORY_REPORTING, stages[3]);
    assertEquals(Stage.BOOKS_AND_RECORDS, stages[4]);
  }

  @Test
  void valueOf() {
    assertEquals(Stage.ALLOCATION, Stage.valueOf("ALLOCATION"));
    assertEquals(Stage.CONFIRMATION, Stage.valueOf("CONFIRMATION"));
    assertEquals(Stage.SETTLEMENT_INSTRUCTION, Stage.valueOf("SETTLEMENT_INSTRUCTION"));
    assertEquals(Stage.REGULATORY_REPORTING, Stage.valueOf("REGULATORY_REPORTING"));
    assertEquals(Stage.BOOKS_AND_RECORDS, Stage.valueOf("BOOKS_AND_RECORDS"));
  }
}
