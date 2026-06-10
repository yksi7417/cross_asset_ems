/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.posttrade.allocation.AccountShare;
import io.crossasset.ems.posttrade.allocation.AllocationPolicy;
import io.crossasset.ems.posttrade.allocation.AllocationTemplate;
import io.crossasset.ems.posttrade.allocation.Fill;
import io.crossasset.ems.posttrade.allocation.InMemoryAllocationService;
import io.crossasset.ems.posttrade.allocation.RoundingPolicy;
import io.crossasset.ems.posttrade.regulatory.RegReportEvent.RegReportAcked;
import io.crossasset.ems.posttrade.stp.InMemoryStpOrchestrator;
import io.crossasset.ems.posttrade.stp.Stage;
import io.crossasset.ems.posttrade.stp.StageHandler.StageContext;
import io.crossasset.ems.posttrade.stp.StageOutcome;
import io.crossasset.ems.posttrade.stp.StageProfile;
import io.crossasset.ems.posttrade.stp.StpOrchestrator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for the TRACE-mock adapter (12.6) and its STP {@code REGULATORY_REPORTING} bridge. */
class TraceMockSubmissionTest {

  private static Map<String, String> fullFields() {
    Map<String, String> f = new HashMap<>();
    f.put("trace_party_id", "BD123");
    f.put("cusip_or_isin", "US123456AB12");
    f.put("executing_broker", "EB1");
    f.put("contra_party_id", "C");
    f.put("side", "1");
    f.put("qty", "100");
    f.put("price", "9950");
    f.put("yield", "452");
    f.put("trade_date", "2026-06-09");
    f.put("settle_date", "2026-06-11");
    return f;
  }

  private static ReportableTrade trade() {
    return new ReportableTrade(
        "TR-1", "CORP_BOND", "US", "US123456AB12", 1, 100, 9950, fullFields());
  }

  private static InMemoryRegulatoryReportingService service(RegulatorAdapter adapter) {
    InMemoryRegulatoryReportingService svc =
        new InMemoryRegulatoryReportingService(RegulatorDeterminer.usDefaults());
    svc.registerProfile(ReportingProfile.trace());
    svc.registerAdapter(adapter);
    return svc;
  }

  @Test
  void traceMock_acksSubmission() {
    InMemoryRegulatoryReportingService svc = service(TraceMockAdapter.acking());
    List<RegReportEvent> events = svc.report(trade(), "AllocationApplied");
    assertThat(events)
        .anySatisfy(
            e ->
                assertThat(e)
                    .isInstanceOfSatisfying(
                        RegReportAcked.class,
                        a -> assertThat(a.regulatorAckRef()).startsWith("TRACE-ACK-")));
    assertThat(svc.stateOf("TR-1:TRACE")).contains(ReportState.ACKED);
  }

  @Test
  void buildPayload_isDeterministic() {
    TraceMockAdapter adapter = TraceMockAdapter.acking();
    assertThat(adapter.buildPayload(trade())).isEqualTo(adapter.buildPayload(trade()));
  }

  @Test
  void stageHandler_traceAcked_complete() {
    RegulatoryStageHandler handler =
        new RegulatoryStageHandler(service(TraceMockAdapter.acking()), ctx -> trade());
    assertThat(handler.handle(new StageContext("F1", "ORD-1", List.of())))
        .isEqualTo(StageOutcome.COMPLETE);
  }

  @Test
  void stageHandler_traceRejected_anomaly() {
    RegulatoryStageHandler handler =
        new RegulatoryStageHandler(service(TraceMockAdapter.rejecting()), ctx -> trade());
    assertThat(handler.handle(new StageContext("F1", "ORD-1", List.of())))
        .isEqualTo(StageOutcome.ANOMALY);
  }

  @Test
  void stageHandler_noApplicableRegulator_notRequired() {
    ReportableTrade nonUs =
        new ReportableTrade("TR-9", "CORP_BOND", "JP", "JP1234567890", 1, 100, 9950, Map.of());
    RegulatoryStageHandler handler =
        new RegulatoryStageHandler(service(TraceMockAdapter.acking()), ctx -> nonUs);
    assertThat(handler.handle(new StageContext("F9", "ORD-9", List.of())))
        .isEqualTo(StageOutcome.NOT_REQUIRED);
  }

  @Test
  void wiredIntoStpPipeline_regulatoryStageCompletes() {
    InMemoryStpOrchestrator stp = new InMemoryStpOrchestrator(new InMemoryAllocationService());
    stp.register(
        Stage.REGULATORY_REPORTING,
        new RegulatoryStageHandler(service(TraceMockAdapter.acking()), ctx -> trade()));

    AllocationTemplate template =
        AllocationTemplate.of(
            "TPL-1",
            1L,
            AllocationPolicy.PRO_RATA,
            RoundingPolicy.DISTRIBUTE_RESIDUAL,
            List.of(new AccountShare("ACC_A", "PB1", 10000)));
    StpOrchestrator.StpResult result =
        stp.ingest(new Fill("F1", "ORD-1", "RTE-1", 100, 9950), template, StageProfile.corpBond());

    assertThat(result.state().stages().get(Stage.REGULATORY_REPORTING))
        .isEqualTo(StageOutcome.COMPLETE);
  }
}
