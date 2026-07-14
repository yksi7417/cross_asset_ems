/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.stp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class StageHandlerTest {

  @Test
  void handleReturnsExpectedOutcome() {
    StageHandler handler = context -> StageOutcome.COMPLETE;
    StageHandler.StageContext context =
        new StageHandler.StageContext("fill-1", "order-1", List.of());
    StageOutcome outcome = handler.handle(context);
    assertEquals(StageOutcome.COMPLETE, outcome);
  }

  @Test
  void stageContextHoldsFields() {
    StageHandler.StageContext context =
        new StageHandler.StageContext("fill-1", "order-1", List.of());
    assertEquals("fill-1", context.fillId());
    assertEquals("order-1", context.orderId());
    assertEquals(List.of(), context.allocations());
  }
}
