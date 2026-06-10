/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.fix.FixGateway;
import io.crossasset.ems.fix.FixMessage;
import io.crossasset.ems.fix.FixTags;
import io.crossasset.ems.fix.OutboundSink;
import io.crossasset.ems.observability.trace.TraceHop;
import io.crossasset.ems.observability.trace.TracePropagator;
import io.crossasset.ems.observability.trace.TraceVerification;
import io.crossasset.ems.observability.trace.TraceVerifier;
import io.crossasset.ems.oms.InMemoryStagedOrderManager;
import io.crossasset.ems.oms.StagedOrderManager;
import io.crossasset.ems.posttrade.allocation.AccountShare;
import io.crossasset.ems.posttrade.allocation.AllocationEvent.AllocationApplied;
import io.crossasset.ems.posttrade.allocation.AllocationPolicy;
import io.crossasset.ems.posttrade.allocation.AllocationTemplate;
import io.crossasset.ems.posttrade.allocation.Fill;
import io.crossasset.ems.posttrade.allocation.InMemoryAllocationService;
import io.crossasset.ems.posttrade.allocation.RoundingPolicy;
import io.crossasset.ems.posttrade.confirmation.ConfirmationNetwork;
import io.crossasset.ems.posttrade.confirmation.ConfirmationStageHandler;
import io.crossasset.ems.posttrade.confirmation.InMemoryConfirmationService;
import io.crossasset.ems.posttrade.confirmation.MatchTolerance;
import io.crossasset.ems.posttrade.confirmation.MockConfirmationNetwork;
import io.crossasset.ems.posttrade.confirmation.TradeRecord;
import io.crossasset.ems.posttrade.regulatory.InMemoryRegulatoryReportingService;
import io.crossasset.ems.posttrade.regulatory.RegulatorDeterminer;
import io.crossasset.ems.posttrade.regulatory.RegulatoryStageHandler;
import io.crossasset.ems.posttrade.regulatory.ReportableTrade;
import io.crossasset.ems.posttrade.regulatory.ReportingProfile;
import io.crossasset.ems.posttrade.regulatory.TraceMockAdapter;
import io.crossasset.ems.posttrade.stp.InMemoryStpOrchestrator;
import io.crossasset.ems.posttrade.stp.Stage;
import io.crossasset.ems.posttrade.stp.StageOutcome;
import io.crossasset.ems.posttrade.stp.StageProfile;
import io.crossasset.ems.posttrade.stp.StpOrchestrator;
import io.crossasset.ems.posttrade.stp.StpState;
import io.crossasset.ems.transport.session.SequenceRecoveryService;
import io.crossasset.ems.validator.ValidationResult;
import io.crossasset.ems.venue.VenueEventSink;
import io.crossasset.ems.venue.VenueRouteRequest;
import io.crossasset.ems.venue.mock.MockVenueAdapter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Task 15.1 — the v0 MVP done-criteria, made executable.
 *
 * <p>Drives one US IG corp bond order end to end: FIX {@code NewOrderSingle} → validator → staged →
 * routed via the mock venue → fill ack → allocation → confirmation → TRACE-mock submission. Asserts
 * a single trace ID through the whole chain and byte-identical replay (the pipeline is a pure
 * function of its inputs, so running it twice produces an identical canonical event log).
 */
class MvpSmokeTest {

  private static final char SOH = '\u0001';
  private static final long SESSION = 100L;
  private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
  private static final String CL_ORD_ID = "CL-IG-001";
  private static final String FIGI = "BBG000CORP01"; // a US IG corp bond
  private static final long QTY = 1_000_000L;
  private static final long PRICE = 9950L; // 99.50

  // ── Recording sinks ──────────────────────────────────────────────────────────

  private static final class RecordingFixSink implements OutboundSink {
    final List<String> wires = new ArrayList<>();

    @Override
    public void deliver(long sessionId, long outboundSeq, String rawFix) {
      wires.add(rawFix);
    }

    FixMessage firstOfType(String msgType) {
      return wires.stream()
          .map(FixMessage::parse)
          .filter(m -> msgType.equals(m.get(FixTags.MSG_TYPE)))
          .findFirst()
          .orElseThrow(() -> new AssertionError("no outbound " + msgType));
    }
  }

  private record VenueFill(long qty, long px, String execId) {}

  private static final class RecordingVenueSink implements VenueEventSink {
    boolean acknowledged;
    VenueFill finalFill;

    @Override
    public void acknowledged(String routeId) {
      acknowledged = true;
    }

    @Override
    public void filled(String routeId, long lastQty, long lastPx, String execId) {
      finalFill = new VenueFill(lastQty, lastPx, execId);
    }

    @Override
    public void pendingNew(String routeId) {}

    @Override
    public void rejected(String routeId, String venueReason) {}

    @Override
    public void partialFill(String routeId, long lastQty, long lastPx, String execId) {}

    @Override
    public void canceled(String routeId) {}

    @Override
    public void cancelRejected(String routeId, int cxlRejReason) {}

    @Override
    public void replaced(String routeId) {}

    @Override
    public void replaceRejected(String routeId, int cxlRejReason) {}
  }

  // ── The pipeline ─────────────────────────────────────────────────────────────

  private record SmokeResult(List<String> eventLog, TraceVerification trace, StpState stp) {}

  private String newOrderSingle() {
    return "8=FIX.4.4"
        + SOH
        + "35=D"
        + SOH
        + "34=1"
        + SOH
        + "49=CLIENT"
        + SOH
        + "56=EMS"
        + SOH
        + "11="
        + CL_ORD_ID
        + SOH
        + "48="
        + FIGI
        + SOH
        + "54=1"
        + SOH
        + "38="
        + QTY
        + SOH
        + "44="
        + PRICE
        + SOH
        + "1=ACC_A"
        + SOH
        + "59=0"
        + SOH
        + "10=000"
        + SOH;
  }

  private Map<String, String> traceFields() {
    Map<String, String> f = new HashMap<>();
    f.put("trace_party_id", "BD123");
    f.put("cusip_or_isin", FIGI);
    f.put("executing_broker", "EB1");
    f.put("contra_party_id", "C");
    f.put("side", "1");
    f.put("qty", Long.toString(QTY));
    f.put("price", Long.toString(PRICE));
    f.put("yield", "452");
    f.put("trade_date", "2026-06-09");
    f.put("settle_date", "2026-06-11");
    return f;
  }

  /** Runs the full MVP chain once and returns a canonical, replay-comparable event log. */
  private SmokeResult runPipeline() {
    List<String> log = new ArrayList<>();
    TracePropagator propagator = new TracePropagator();
    TraceVerifier verifier = new TraceVerifier();

    // ── Client FIX edge (8.1): mint + stamp the trace at first sight, then decode → stage. ──
    SequenceRecoveryService sessions = new SequenceRecoveryService(() -> 0L);
    StagedOrderManager oms =
        new InMemoryStagedOrderManager(req -> new ValidationResult.Pass(req.requestId()));
    RecordingFixSink fixSink = new RecordingFixSink();
    FixGateway gateway = new FixGateway(oms, sessions, fixSink, "EMS", "CLIENT");

    propagator.stamp(CL_ORD_ID, TRACE_ID);
    verifier.observe(CL_ORD_ID, TraceHop.FIX_IN, propagator.lookup(CL_ORD_ID).orElseThrow());

    gateway.onLogon(SESSION, 1L);
    gateway.onInbound(SESSION, newOrderSingle());
    verifier.observe(CL_ORD_ID, TraceHop.VALIDATE, propagator.lookup(CL_ORD_ID).orElseThrow());
    verifier.observe(CL_ORD_ID, TraceHop.STAGE, propagator.lookup(CL_ORD_ID).orElseThrow());

    FixMessage execReport = fixSink.firstOfType("8");
    String orderId = execReport.get(FixTags.ORDER_ID);
    log.add(
        "EXEC_REPORT ordStatus="
            + execReport.get(FixTags.ORD_STATUS)
            + " clOrdId="
            + execReport.get(FixTags.CL_ORD_ID)
            + " orderId="
            + orderId);

    // ── Route via the mock venue (11.2): single trace re-attached by ClOrdID at venue-out. ──
    String routeId = "RTE-" + orderId;
    RecordingVenueSink venueSink = new RecordingVenueSink();
    MockVenueAdapter venue = MockVenueAdapter.marketAxess(venueSink);
    verifier.observe(CL_ORD_ID, TraceHop.ROUTE, propagator.lookup(CL_ORD_ID).orElseThrow());
    venue.submit(new VenueRouteRequest(routeId, CL_ORD_ID, FIGI, 1, QTY, PRICE));
    verifier.observe(CL_ORD_ID, TraceHop.VENUE_OUT, propagator.lookup(CL_ORD_ID).orElseThrow());
    log.add("VENUE_ACK route=" + routeId + " ack=" + venueSink.acknowledged);
    log.add("VENUE_FILL qty=" + venueSink.finalFill.qty() + " px=" + venueSink.finalFill.px());

    // ── Post-trade pipeline (12.1 alloc → 12.2 STP → 12.3 confirm → 12.6 TRACE-mock). ──
    InMemoryStpOrchestrator stp = new InMemoryStpOrchestrator(new InMemoryAllocationService());

    TradeRecord ourTrade =
        new TradeRecord(
            routeId,
            FIGI,
            1,
            QTY,
            venueSink.finalFill.px(),
            0,
            "2026-06-09",
            "2026-06-11",
            "CPTY-X");
    ConfirmationNetwork confNet =
        MockConfirmationNetwork.marketAxessPostTrade().withCounterpartyRecord(ourTrade);
    stp.register(
        Stage.CONFIRMATION,
        new ConfirmationStageHandler(
            new InMemoryConfirmationService(), confNet, MatchTolerance.exact(), ctx -> ourTrade));

    InMemoryRegulatoryReportingService reg =
        new InMemoryRegulatoryReportingService(RegulatorDeterminer.usDefaults());
    reg.registerProfile(ReportingProfile.trace());
    reg.registerAdapter(TraceMockAdapter.acking());
    ReportableTrade reportable =
        new ReportableTrade(
            "TR-" + routeId,
            "CORP_BOND",
            "US",
            FIGI,
            1,
            QTY,
            venueSink.finalFill.px(),
            traceFields());
    stp.register(Stage.REGULATORY_REPORTING, new RegulatoryStageHandler(reg, ctx -> reportable));

    AllocationTemplate template =
        AllocationTemplate.of(
            "TPL-IG",
            1L,
            AllocationPolicy.PRO_RATA,
            RoundingPolicy.DISTRIBUTE_RESIDUAL,
            List.of(
                new AccountShare("ACC_A", "PB1", 6000), new AccountShare("ACC_B", "PB1", 4000)));
    Fill fill =
        new Fill(
            venueSink.finalFill.execId(),
            orderId,
            routeId,
            venueSink.finalFill.qty(),
            venueSink.finalFill.px());
    StpOrchestrator.StpResult stpResult = stp.ingest(fill, template, StageProfile.corpBond());

    for (var event : stpResult.allocationEvents()) {
      if (event instanceof AllocationApplied applied) {
        log.add(
            "ALLOC acct=" + applied.account() + " qty=" + applied.qty() + " px=" + applied.price());
      }
    }
    for (Stage stage : StageProfile.corpBond().downstreamStages()) {
      log.add("STAGE " + stage + "=" + stpResult.state().stages().get(stage));
    }
    log.add("OVERALL=" + stpResult.state().overall());

    TraceVerification trace = verifier.verify(CL_ORD_ID, EnumSet.allOf(TraceHop.class));
    log.add(
        "TRACE id="
            + trace.traceId()
            + " single="
            + trace.singleTraceId()
            + " complete="
            + trace.complete());

    return new SmokeResult(log, trace, stpResult.state());
  }

  // ── The done-criteria assertions ─────────────────────────────────────────────

  @Test
  void endToEnd_singleTraceId_allStagesComplete() {
    SmokeResult result = runPipeline();

    // Single trace ID unbroken FIX-in → venue-out.
    assertThat(result.trace().traceId()).isEqualTo(TRACE_ID);
    assertThat(result.trace().singleTraceId()).isTrue();
    assertThat(result.trace().complete()).isTrue();

    // Full post-trade tail completed: allocation → confirmation → TRACE-mock.
    assertThat(result.stp().stages().get(Stage.CONFIRMATION)).isEqualTo(StageOutcome.COMPLETE);
    assertThat(result.stp().stages().get(Stage.REGULATORY_REPORTING))
        .isEqualTo(StageOutcome.COMPLETE);
    assertThat(result.stp().overall()).isEqualTo(StpState.Overall.COMPLETE);

    // The fill allocated in full (60/40 of 1mm).
    assertThat(result.eventLog()).contains("ALLOC acct=ACC_A qty=600000 px=9950");
    assertThat(result.eventLog()).contains("ALLOC acct=ACC_B qty=400000 px=9950");
  }

  @Test
  void replay_isByteIdentical() {
    List<String> first = runPipeline().eventLog();
    List<String> second = runPipeline().eventLog();
    assertThat(second).isEqualTo(first);
  }
}
