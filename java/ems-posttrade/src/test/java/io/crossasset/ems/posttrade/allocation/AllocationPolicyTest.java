/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.allocation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AllocationPolicyTest {

  @Test
  void policySetIsStable() {
    // event-log replay depends on these names; adding/renaming is a breaking change
    assertArrayEquals(
        new AllocationPolicy[] {
          AllocationPolicy.PRO_RATA,
          AllocationPolicy.AVG_PRICE,
          AllocationPolicy.SEQUENCED,
          AllocationPolicy.CUSTOM
        },
        AllocationPolicy.values());
  }

  @Test
  void valueOfRoundTripsForPersistedNames() {
    for (AllocationPolicy p : AllocationPolicy.values()) {
      assertEquals(p, AllocationPolicy.valueOf(p.name()));
    }
  }

  @Test
  void unknownPolicyNameRejected() {
    assertThrows(IllegalArgumentException.class, () -> AllocationPolicy.valueOf("FIFO"));
  }
}
