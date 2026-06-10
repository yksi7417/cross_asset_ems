/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.observability.trace.TraceHop;
import io.crossasset.ems.observability.trace.TracePropagator;
import io.crossasset.ems.observability.trace.TraceVerification;
import io.crossasset.ems.observability.trace.TraceVerifier;
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
import io.crossasset.ems.posttrade.confirmation.MockConfirmationNetwork;
import io.crossasset.ems.posttrade.confirmation.TradeRecord;
import io.crossasset.ems.posttrade.coverage.AssetClassProfile;
import io.crossasset.ems.posttrade.coverage.AssetClassProfiles;
import io.crossasset.ems.posttrade.coverage.Coverage;
import io.crossasset.ems.posttrade.regulatory.InMemoryRegulatoryReportingService;
import io.crossasset.ems.posttrade.regulatory.MockRegulatorAdapter;
import io.crossasset.ems.posttrade.regulatory.Regulator;
import io.crossasset.ems.posttrade.regulatory.RegulatorDeterminer;
import io.crossasset.ems.posttrade.regulatory.RegulatoryStageHandler;
import io.crossasset.ems.posttrade.regulatory.ReportableTrade;
import io.crossasset.ems.posttrade.regulatory.ReportingProfile;
import io.crossasset.ems.posttrade.stp.InMemoryStpOrchestrator;
import io.crossasset.ems.posttrade.stp.Stage;
import io.crossasset.ems.posttrade.stp.StageOutcome;
import io.crossasset.ems.posttrade.stp.StpOrchestrator;
import io.crossasset.ems.posttrade.stp.StpState;
import io.crossasset.ems.venue.VenueEventSink;
import io.crossasset.ems.venue.VenueRouteRequest;
import io.crossasset.ems.venue.mock.MockVenueAdapter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Task 16.3 — cross-asset end-to-end smoke. Drives each {@link Coverage} label (US equity,
 * preferred, treasury, listed fut/opt, FX spot, FX forward, and corp bond) through fill →
 * allocation → STP → confirmation → reporting using its {@link AssetClassProfile}, asserting
 * completion, a single trace ID across the chain, and byte-identical replay. The client FIX edge
 * itself is proven for corp bond in {@link MvpSmokeTest}; this test focuses on per-asset post-trade
 * differentiation.
 */
class CrossAssetSmokeTest {

  private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";

  private record VenueFill(long qty, long px, String execId) {}

  private static final class RecordingVenueSink implements VenueEventSink {
    VenueFill finalFill;

    @Override
    public void filled(String routeId, long lastQty, long lastPx, String execId) {
      finalFill = new VenueFill(lastQty, lastPx, execId);
    }

    @Override
    public void acknowledged(String routeId) {}

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

  private record CoverageResult(List<String> eventLog, TraceVerification trace, StpState stp) {}

  /** Runs one asset class through fill → post-trade and returns a canonical, replayable log. */
  private CoverageResult runCoverage(Coverage coverage) {
    AssetClassProfile profile = AssetClassProfiles.of(coverage);
    String clOrdId = "CL-" + coverage;
    String routeId = "RTE-" + coverage;
    String instrument = "INST-" + coverage;
    long qty = profile.allocationLotSize() * 100L; // a clean multiple of the lot size
    long px = 9950L;

    List<String> log = new ArrayList<>();
    TracePropagator propagator = new TracePropagator();
    TraceVerifier verifier = new TraceVerifier();

    // Trade hops: trace minted at the edge, re-attached by ClOrdID through to the venue.
    propagator.stamp(clOrdId, TRACE_ID);
    verifier.observe(clOrdId, TraceHop.FIX_IN, propagator.lookup(clOrdId).orElseThrow());
    verifier.observe(clOrdId, TraceHop.VALIDATE, propagator.lookup(clOrdId).orElseThrow());
    verifier.observe(clOrdId, TraceHop.STAGE, propagator.lookup(clOrdId).orElseThrow());

    RecordingVenueSink venueSink = new RecordingVenueSink();
    MockVenueAdapter venue = MockVenueAdapter.marketAxess(venueSink);
    verifier.observe(clOrdId, TraceHop.ROUTE, propagator.lookup(clOrdId).orElseThrow());
    venue.submit(new VenueRouteRequest(routeId, clOrdId, instrument, 1, qty, px));
    verifier.observe(clOrdId, TraceHop.VENUE_OUT, propagator.lookup(clOrdId).orElseThrow());
    log.add("FILL qty=" + venueSink.finalFill.qty() + " px=" + venueSink.finalFill.px());

    // Post-trade pipeline driven by the asset-class profile.
    InMemoryStpOrchestrator stp = new InMemoryStpOrchestrator(new InMemoryAllocationService());
    List<Stage> stages = profile.stageProfile().downstreamStages();

    if (stages.contains(Stage.CONFIRMATION)) {
      TradeRecord ourTrade =
          new TradeRecord(routeId, instrument, 1, qty, px, 0, "2026-06-09", "2026-06-11", "CPTY-X");
      ConfirmationNetwork net =
          MockConfirmationNetwork.marketAxessPostTrade().withCounterpartyRecord(ourTrade);
      stp.register(
          Stage.CONFIRMATION,
          new ConfirmationStageHandler(
              new InMemoryConfirmationService(),
              net,
              profile.confirmationTolerance(),
              ctx -> ourTrade));
    }

    if (stages.contains(Stage.REGULATORY_REPORTING)) {
      InMemoryRegulatoryReportingService reg =
          new InMemoryRegulatoryReportingService(RegulatorDeterminer.crossAssetUs());
      for (Regulator regulator : profile.regulators()) {
        reg.registerProfile(ReportingProfile.mock(regulator));
        reg.registerAdapter(MockRegulatorAdapter.acking(regulator));
      }
      ReportableTrade reportable =
          new ReportableTrade(
              "TR-" + routeId,
              coverage.name(),
              "US",
              instrument,
              1,
              qty,
              px,
              Map.of("side", "1", "qty", Long.toString(qty), "price", Long.toString(px)));
      stp.register(Stage.REGULATORY_REPORTING, new RegulatoryStageHandler(reg, ctx -> reportable));
    }

    AllocationTemplate template =
        AllocationTemplate.of(
            "TPL-" + coverage,
            1L,
            AllocationPolicy.PRO_RATA,
            RoundingPolicy.DISTRIBUTE_RESIDUAL,
            List.of(new AccountShare("ACC_A", "PB1", 6000), new AccountShare("ACC_B", "PB1", 4000)),
            profile.allocationLotSize());
    Fill fill =
        new Fill(
            venueSink.finalFill.execId(),
            "ORD-" + coverage,
            routeId,
            venueSink.finalFill.qty(),
            venueSink.finalFill.px());
    StpOrchestrator.StpResult result = stp.ingest(fill, template, profile.stageProfile());

    for (var event : result.allocationEvents()) {
      if (event instanceof AllocationApplied applied) {
        log.add("ALLOC acct=" + applied.account() + " qty=" + applied.qty());
      }
    }
    for (Stage stage : stages) {
      log.add("STAGE " + stage + "=" + result.state().stages().get(stage));
    }
    log.add("OVERALL=" + result.state().overall());

    TraceVerification trace = verifier.verify(clOrdId, EnumSet.allOf(TraceHop.class));
    log.add("TRACE id=" + trace.traceId() + " complete=" + trace.complete());
    return new CoverageResult(log, trace, result.state());
  }

  @Test
  void everyAssetClass_completesWithSingleTraceId() {
    for (Coverage coverage : Coverage.values()) {
      CoverageResult result = runCoverage(coverage);

      assertThat(result.trace().traceId()).as("%s trace id", coverage).isEqualTo(TRACE_ID);
      assertThat(result.trace().complete()).as("%s trace complete", coverage).isTrue();
      assertThat(result.stp().overall())
          .as("%s overall", coverage)
          .isEqualTo(StpState.Overall.COMPLETE);
      // No stage anomalied.
      assertThat(result.stp().stages().values())
          .as("%s stages", coverage)
          .noneMatch(o -> o == StageOutcome.ANOMALY);
      // The fill allocated in full (qty is a clean multiple of the lot size).
      long allocated =
          result.eventLog().stream()
              .filter(s -> s.startsWith("ALLOC "))
              .mapToLong(s -> Long.parseLong(s.substring(s.indexOf("qty=") + 4)))
              .sum();
      assertThat(allocated)
          .as("%s fully allocated", coverage)
          .isEqualTo(AssetClassProfiles.of(coverage).allocationLotSize() * 100L);
    }
  }

  @Test
  void everyAssetClass_replaysByteIdentical() {
    for (Coverage coverage : Coverage.values()) {
      assertThat(runCoverage(coverage).eventLog())
          .as("%s replay", coverage)
          .isEqualTo(runCoverage(coverage).eventLog());
    }
  }
}
