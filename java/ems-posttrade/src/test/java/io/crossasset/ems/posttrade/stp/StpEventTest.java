/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.stp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class StpEventTest {

  @Test
  void fillIngestedHoldsFields() {
    StpEvent.StpFillIngested event = new StpEvent.StpFillIngested("fill-1", "order-1");
    assertEquals("fill-1", event.fillId());
    assertEquals("order-1", event.orderId());
  }

  @Test
  void pipelineStartedHoldsStages() {
    List<Stage> stages = List.of(Stage.ALLOCATION, Stage.CONFIRMATION);
    StpEvent.StpPipelineStarted event = new StpEvent.StpPipelineStarted("fill-1", stages);
    assertEquals("fill-1", event.fillId());
    assertEquals(stages, event.stagesPlanned());
  }

  @Test
  void stageCompleteHoldsOutcome() {
    StpEvent.StpStageComplete event =
        new StpEvent.StpStageComplete("fill-1", Stage.CONFIRMATION, StageOutcome.COMPLETE);
    assertEquals("fill-1", event.fillId());
    assertEquals(Stage.CONFIRMATION, event.stage());
    assertEquals(StageOutcome.COMPLETE, event.outcome());
  }

  @Test
  void stageAnomalyHoldsReason() {
    StpEvent.StpStageAnomaly event =
        new StpEvent.StpStageAnomaly("fill-1", Stage.CONFIRMATION, "failed", "ops-queue");
    assertEquals("fill-1", event.fillId());
    assertEquals("failed", event.reason());
    assertEquals("ops-queue", event.opsQueue());
  }

  @Test
  void pipelineCompleteHoldsSummary() {
    StpEvent.StpPipelineComplete event = new StpEvent.StpPipelineComplete("fill-1", "all done");
    assertEquals("fill-1", event.fillId());
    assertEquals("all done", event.summary());
  }

  @Test
  void stageRetryRequestedHoldsDetails() {
    StpEvent.StpStageRetryRequested event =
        new StpEvent.StpStageRetryRequested("fill-1", Stage.CONFIRMATION, "user", "retry reason");
    assertEquals("fill-1", event.fillId());
    assertEquals("user", event.by());
    assertEquals("retry reason", event.reason());
  }
}
