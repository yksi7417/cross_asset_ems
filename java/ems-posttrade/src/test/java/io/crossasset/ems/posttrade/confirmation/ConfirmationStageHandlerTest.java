/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.confirmation;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.posttrade.allocation.AccountShare;
import io.crossasset.ems.posttrade.allocation.AllocationPolicy;
import io.crossasset.ems.posttrade.allocation.AllocationTemplate;
import io.crossasset.ems.posttrade.allocation.Fill;
import io.crossasset.ems.posttrade.allocation.InMemoryAllocationService;
import io.crossasset.ems.posttrade.allocation.RoundingPolicy;
import io.crossasset.ems.posttrade.stp.InMemoryStpOrchestrator;
import io.crossasset.ems.posttrade.stp.Stage;
import io.crossasset.ems.posttrade.stp.StageHandler.StageContext;
import io.crossasset.ems.posttrade.stp.StageOutcome;
import io.crossasset.ems.posttrade.stp.StageProfile;
import io.crossasset.ems.posttrade.stp.StpOrchestrator;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for {@link ConfirmationStageHandler} and its wiring into the STP pipeline. */
class ConfirmationStageHandlerTest {

  private static TradeRecord ourRecord() {
    return new TradeRecord(
        "TR-F1", "US123456AB12", 1, 100, 9950, 12, "2026-06-09", "2026-06-11", "CPTY-X");
  }

  private final ConfirmationStageHandler.TradeRecordBuilder builder = ctx -> ourRecord();

  @Test
  void matchedConfirmation_yieldsComplete() {
    ConfirmationNetwork net =
        MockConfirmationNetwork.marketAxessPostTrade().withCounterpartyRecord(ourRecord());
    ConfirmationStageHandler handler =
        new ConfirmationStageHandler(
            new InMemoryConfirmationService(), net, MatchTolerance.exact(), builder);

    StageOutcome outcome = handler.handle(new StageContext("F1", "ORD-1", List.of()));
    assertThat(outcome).isEqualTo(StageOutcome.COMPLETE);
  }

  @Test
  void unmatchedConfirmation_yieldsAnomaly() {
    TradeRecord differing =
        new TradeRecord(
            "TR-F1", "US123456AB12", 1, 100, 9999, 12, "2026-06-09", "2026-06-11", "CPTY-X");
    ConfirmationNetwork net =
        MockConfirmationNetwork.marketAxessPostTrade().withCounterpartyRecord(differing);
    ConfirmationStageHandler handler =
        new ConfirmationStageHandler(
            new InMemoryConfirmationService(), net, MatchTolerance.exact(), builder);

    StageOutcome outcome = handler.handle(new StageContext("F1", "ORD-1", List.of()));
    assertThat(outcome).isEqualTo(StageOutcome.ANOMALY);
  }

  @Test
  void wiredIntoStpPipeline_confirmationStageCompletes() {
    ConfirmationNetwork net =
        MockConfirmationNetwork.marketAxessPostTrade().withCounterpartyRecord(ourRecord());
    InMemoryStpOrchestrator stp = new InMemoryStpOrchestrator(new InMemoryAllocationService());
    stp.register(
        Stage.CONFIRMATION,
        new ConfirmationStageHandler(
            new InMemoryConfirmationService(), net, MatchTolerance.exact(), builder));

    AllocationTemplate template =
        AllocationTemplate.of(
            "TPL-1",
            1L,
            AllocationPolicy.PRO_RATA,
            RoundingPolicy.DISTRIBUTE_RESIDUAL,
            List.of(new AccountShare("ACC_A", "PB1", 10000)));
    StpOrchestrator.StpResult result =
        stp.ingest(new Fill("F1", "ORD-1", "RTE-1", 100, 9950), template, StageProfile.corpBond());

    assertThat(result.state().stages().get(Stage.CONFIRMATION)).isEqualTo(StageOutcome.COMPLETE);
  }
}
