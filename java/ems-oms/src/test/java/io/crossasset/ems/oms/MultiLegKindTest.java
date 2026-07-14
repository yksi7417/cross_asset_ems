/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MultiLegKindTest {

  @Test
  void values_containsAllExpectedKinds() {
    MultiLegKind[] values = MultiLegKind.values();
    assertEquals(6, values.length);

    assertNotNull(MultiLegKind.valueOf("SWAP"));
    assertNotNull(MultiLegKind.valueOf("SPREAD"));
    assertNotNull(MultiLegKind.valueOf("ROLL"));
    assertNotNull(MultiLegKind.valueOf("DELTA_HEDGE"));
    assertNotNull(MultiLegKind.valueOf("PT"));
    assertNotNull(MultiLegKind.valueOf("CUSTOM"));
  }

  @Test
  void valueOf_caseSensitive() {
    assertEquals(MultiLegKind.SWAP, MultiLegKind.valueOf("SWAP"));
    assertEquals(MultiLegKind.SPREAD, MultiLegKind.valueOf("SPREAD"));
    assertEquals(MultiLegKind.ROLL, MultiLegKind.valueOf("ROLL"));
    assertEquals(MultiLegKind.DELTA_HEDGE, MultiLegKind.valueOf("DELTA_HEDGE"));
    assertEquals(MultiLegKind.PT, MultiLegKind.valueOf("PT"));
    assertEquals(MultiLegKind.CUSTOM, MultiLegKind.valueOf("CUSTOM"));
  }

  @Test
  void valueOf_invalidName_throwsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> MultiLegKind.valueOf("INVALID"));
    assertThrows(IllegalArgumentException.class, () -> MultiLegKind.valueOf("swap"));
    assertThrows(IllegalArgumentException.class, () -> MultiLegKind.valueOf(""));
  }

  @Test
  void eachKind_hasCorrectOrdinal() {
    assertEquals(0, MultiLegKind.SWAP.ordinal());
    assertEquals(1, MultiLegKind.SPREAD.ordinal());
    assertEquals(2, MultiLegKind.ROLL.ordinal());
    assertEquals(3, MultiLegKind.DELTA_HEDGE.ordinal());
    assertEquals(4, MultiLegKind.PT.ordinal());
    assertEquals(5, MultiLegKind.CUSTOM.ordinal());
  }

  @Test
  void name_returnsUpperCaseString() {
    assertEquals("SWAP", MultiLegKind.SWAP.name());
    assertEquals("SPREAD", MultiLegKind.SPREAD.name());
    assertEquals("ROLL", MultiLegKind.ROLL.name());
    assertEquals("DELTA_HEDGE", MultiLegKind.DELTA_HEDGE.name());
    assertEquals("PT", MultiLegKind.PT.name());
    assertEquals("CUSTOM", MultiLegKind.CUSTOM.name());
  }
}
