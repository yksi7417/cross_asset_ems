/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.stp;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.posttrade.allocation.AccountShare;
import io.crossasset.ems.posttrade.allocation.AllocationPolicy;
import io.crossasset.ems.posttrade.allocation.AllocationTemplate;
import io.crossasset.ems.posttrade.allocation.AllocationValidator;
import io.crossasset.ems.posttrade.allocation.Fill;
import io.crossasset.ems.posttrade.allocation.InMemoryAllocationService;
import io.crossasset.ems.posttrade.allocation.RoundingPolicy;
import io.crossasset.ems.posttrade.stp.StpEvent.StpPipelineComplete;
import io.crossasset.ems.posttrade.stp.StpState.AllocationState;
import io.crossasset.ems.posttrade.stp.StpState.Overall;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for {@link InMemoryStpOrchestrator}. */
class StpOrchestratorTest {

  private static final StageHandler COMPLETE = ctx -> StageOutcome.COMPLETE;
  private static final StageHandler ANOMALY = ctx -> StageOutcome.ANOMALY;

  private static AllocationTemplate template() {
    return AllocationTemplate.of(
        "TPL-1",
        1L,
        AllocationPolicy.PRO_RATA,
        RoundingPolicy.DISTRIBUTE_RESIDUAL,
        List.of(new AccountShare("ACC_A", "PB1", 6000), new AccountShare("ACC_B", "PB1", 4000)));
  }

  private static Fill fill() {
    return new Fill("F1", "ORD-1", "RTE-1", 100, 9950L);
  }

  private static InMemoryStpOrchestrator orchestrator() {
    return new InMemoryStpOrchestrator(new InMemoryAllocationService());
  }

  @Test
  void allStagesComplete_overallComplete() {
    InMemoryStpOrchestrator stp = orchestrator();
    for (Stage s : StageProfile.corpBond().downstreamStages()) {
      stp.register(s, COMPLETE);
    }

    StpOrchestrator.StpResult result = stp.ingest(fill(), template(), StageProfile.corpBond());

    assertThat(result.state().allocation()).isEqualTo(AllocationState.APPLIED);
    assertThat(result.state().overall()).isEqualTo(Overall.COMPLETE);
    assertThat(result.state().stages().values()).allMatch(o -> o == StageOutcome.COMPLETE);
    assertThat(result.events()).anyMatch(e -> e instanceof StpPipelineComplete);
  }

  @Test
  void unregisteredStages_areNotRequired_pipelineStillCompletes() {
    InMemoryStpOrchestrator stp = orchestrator(); // no handlers registered

    StpOrchestrator.StpResult result = stp.ingest(fill(), template(), StageProfile.corpBond());

    assertThat(result.state().stages().values()).allMatch(o -> o == StageOutcome.NOT_REQUIRED);
    assertThat(result.state().overall()).isEqualTo(Overall.COMPLETE);
  }

  @Test
  void allocationAnomaly_haltsPipeline_overallAnomaly() {
    AllocationValidator deny = (share, tpl) -> "account closed";
    InMemoryStpOrchestrator stp = new InMemoryStpOrchestrator(new InMemoryAllocationService(deny));
    for (Stage s : StageProfile.corpBond().downstreamStages()) {
      stp.register(s, COMPLETE);
    }

    StpOrchestrator.StpResult result = stp.ingest(fill(), template(), StageProfile.corpBond());

    assertThat(result.state().allocation()).isEqualTo(AllocationState.ANOMALY);
    assertThat(result.state().overall()).isEqualTo(Overall.ANOMALY);
    // Downstream stages never ran.
    assertThat(result.state().stages()).isEmpty();
  }

  @Test
  void deferredTemplate_pausesPipeline() {
    InMemoryStpOrchestrator stp = orchestrator();

    StpOrchestrator.StpResult result =
        stp.ingest(fill(), AllocationTemplate.deferred("DEF"), StageProfile.corpBond());

    assertThat(result.state().allocation()).isEqualTo(AllocationState.DEFERRED);
    assertThat(result.state().overall()).isEqualTo(Overall.IN_PROGRESS);
    assertThat(result.state().stages()).isEmpty();
  }

  @Test
  void stageAnomaly_isIndependent_otherStagesStillRun() {
    InMemoryStpOrchestrator stp = orchestrator();
    stp.register(Stage.CONFIRMATION, ANOMALY);
    stp.register(Stage.SETTLEMENT_INSTRUCTION, COMPLETE);
    stp.register(Stage.REGULATORY_REPORTING, COMPLETE);
    stp.register(Stage.BOOKS_AND_RECORDS, COMPLETE);

    StpOrchestrator.StpResult result = stp.ingest(fill(), template(), StageProfile.corpBond());

    assertThat(result.state().stages().get(Stage.CONFIRMATION)).isEqualTo(StageOutcome.ANOMALY);
    assertThat(result.state().stages().get(Stage.SETTLEMENT_INSTRUCTION))
        .isEqualTo(StageOutcome.COMPLETE);
    assertThat(result.state().stages().get(Stage.REGULATORY_REPORTING))
        .isEqualTo(StageOutcome.COMPLETE);
    assertThat(result.state().overall()).isEqualTo(Overall.ANOMALY);
  }

  @Test
  void resumeStage_afterFix_completesPipeline() {
    InMemoryStpOrchestrator stp = orchestrator();
    stp.register(Stage.CONFIRMATION, ANOMALY);
    stp.register(Stage.SETTLEMENT_INSTRUCTION, COMPLETE);
    stp.register(Stage.REGULATORY_REPORTING, COMPLETE);
    stp.register(Stage.BOOKS_AND_RECORDS, COMPLETE);
    stp.ingest(fill(), template(), StageProfile.corpBond());

    // Operator fixes the confirmation artefact and re-runs just that stage.
    stp.register(Stage.CONFIRMATION, COMPLETE);
    StpOrchestrator.StpResult result =
        stp.resumeStage("F1", Stage.CONFIRMATION, "ops-user", "amended counterparty");

    assertThat(result.state().stages().get(Stage.CONFIRMATION)).isEqualTo(StageOutcome.COMPLETE);
    assertThat(result.state().overall()).isEqualTo(Overall.COMPLETE);
    assertThat(result.events()).anyMatch(e -> e instanceof StpPipelineComplete);
  }
}
