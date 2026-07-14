/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NetGroupTest {

  @Test
  void netGroup_fields() {
    var group =
        new NetGroup(
            "groupId",
            null,
            "figi",
            "ccyPair",
            "valueDate",
            "accountGroup",
            null,
            List.of(),
            List.of(),
            0,
            0,
            0,
            0,
            0,
            0,
            Map.of());
    assertEquals("groupId", group.groupId());
    assertNull(group.parentOrderId());
    assertEquals("figi", group.figi());
    assertEquals("ccyPair", group.ccyPair());
    assertEquals("valueDate", group.valueDate());
    assertEquals("accountGroup", group.accountGroup());
    assertNull(group.pac());
    assertNotNull(group.buyChildIds());
    assertTrue(group.buyChildIds().isEmpty());
    assertNotNull(group.sellChildIds());
    assertTrue(group.sellChildIds().isEmpty());
    assertEquals(0, group.buyQty());
    assertEquals(0, group.sellQty());
    assertEquals(0, group.residualQty());
    assertEquals(0, group.residualSide());
    assertEquals(0, group.matchedQty());
    assertEquals(0, group.parentFilled());
    assertNotNull(group.allocations());
    assertTrue(group.allocations().isEmpty());
  }
}
