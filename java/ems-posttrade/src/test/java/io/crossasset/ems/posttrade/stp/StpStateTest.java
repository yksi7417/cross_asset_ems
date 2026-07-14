/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.stp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class StpStateTest {

  @Test
  void stateHoldsFields() {
    Map<Stage, StageOutcome> stages = Map.of(Stage.CONFIRMATION, StageOutcome.COMPLETE);
    StpState state =
        new StpState(
            "fill-1",
            "order-1",
            StpState.AllocationState.APPLIED,
            stages,
            StpState.Overall.COMPLETE);
    assertEquals("fill-1", state.fillId());
    assertEquals("order-1", state.orderId());
    assertEquals(StpState.AllocationState.APPLIED, state.allocation());
    assertEquals(StpState.Overall.COMPLETE, state.overall());
  }

  @Test
  void allocationStateValues() {
    StpState.AllocationState[] values = StpState.AllocationState.values();
    assertEquals(4, values.length);
    assertNotNull(StpState.AllocationState.PENDING);
    assertNotNull(StpState.AllocationState.APPLIED);
    assertNotNull(StpState.AllocationState.DEFERRED);
    assertNotNull(StpState.AllocationState.ANOMALY);
  }

  @Test
  void overallValues() {
    StpState.Overall[] values = StpState.Overall.values();
    assertEquals(3, values.length);
    assertNotNull(StpState.Overall.IN_PROGRESS);
    assertNotNull(StpState.Overall.COMPLETE);
    assertNotNull(StpState.Overall.ANOMALY);
  }
}
