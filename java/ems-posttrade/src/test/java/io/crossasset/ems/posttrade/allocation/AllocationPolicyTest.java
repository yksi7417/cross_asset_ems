/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class AllocationPolicyTest {

  @Test
  void valuesExist() {
    AllocationPolicy[] values = AllocationPolicy.values();
    assertNotNull(values);
    assertEquals(4, values.length);
  }

  @Test
  void proRataIsFirst() {
    assertEquals(AllocationPolicy.PRO_RATA, AllocationPolicy.valueOf("PRO_RATA"));
  }

  @Test
  void avgPriceExists() {
    assertEquals(AllocationPolicy.AVG_PRICE, AllocationPolicy.valueOf("AVG_PRICE"));
  }

  @Test
  void sequencedExists() {
    assertEquals(AllocationPolicy.SEQUENCED, AllocationPolicy.valueOf("SEQUENCED"));
  }

  @Test
  void customExists() {
    assertEquals(AllocationPolicy.CUSTOM, AllocationPolicy.valueOf("CUSTOM"));
  }
}
