/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory.cat;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.posttrade.regulatory.InMemoryRegulatoryReportingService;
import io.crossasset.ems.posttrade.regulatory.RegReportEvent;
import io.crossasset.ems.posttrade.regulatory.RegulatorDeterminer;
import io.crossasset.ems.posttrade.regulatory.Regulator;
import io.crossasset.ems.posttrade.regulatory.ReportableTrade;
import io.crossasset.ems.posttrade.regulatory.ReportingProfile;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * CAT submission (12.12): full order-lifecycle capture (MENO→MEOR→MEOM→MEOT→MEOC) batched
 * deterministically, nack-and-repair semantics, and the trade-leg adapter on the 12.5 pipeline.
 */
class CatReporterTest {

  @Test
  void lifecycleEventsBatchInCaptureOrder_payloadIsDeterministic() {
    CatReporter reporter = new CatReporter(CatReporter.Wire.mock(true), "IMID-DEMO");
    reporter.newOrder("ORD-1", 1_000L, "AAPL", 1, 500L, 1_824_500L);
    reporter.orderRoute("ORD-1", 2_000L, "RTE-1", "XNAS", 500L);
    reporter.orderModified("ORD-1", 3_000L, 400L, 1_820_000L);
    reporter.orderTrade("ORD-1", 4_000L, "EXEC-1", 400L, 1_820_000L, "XNAS");
    reporter.orderCanceled("ORD-1", 5_000L, 0L);
    assertThat(reporter.pending()).hasSize(5);

    CatReporter.BatchResult result = reporter.submitBatch();
    assertThat(result.acked()).isTrue();
    assertThat(result.eventCount()).isEqualTo(5);
    assertThat(result.batchId()).isEqualTo("IMID-DEMO-B1");
    assertThat(reporter.pending()).isEmpty();

    // Replay determinism: an identical lifecycle produces the identical ack ref (same bytes).
    CatReporter replay = new CatReporter(CatReporter.Wire.mock(true), "IMID-DEMO");
    replay.newOrder("ORD-1", 1_000L, "AAPL", 1, 500L, 1_824_500L);
    replay.orderRoute("ORD-1", 2_000L, "RTE-1", "XNAS", 500L);
    replay.orderModified("ORD-1", 3_000L, 400L, 1_820_000L);
    replay.orderTrade("ORD-1", 4_000L, "EXEC-1", 400L, 1_820_000L, "XNAS");
    replay.orderCanceled("ORD-1", 5_000L, 0L);
    assertThat(replay.submitBatch().ackRef()).isEqualTo(result.ackRef());
  }

  @Test
  void eventPayloadsCarryCatTaxonomyCodesAndOrderLinkage() {
    CatReporter reporter = new CatReporter(CatReporter.Wire.mock(true), "IMID-DEMO");
    CatEvent neworder = reporter.newOrder("ORD-9", 10L, "MSFT", 2, 100L, null);
    CatEvent route = reporter.orderRoute("ORD-9", 20L, "RTE-9", "ARCX", 100L);

    assertThat(neworder.toPayload()).startsWith("MENO|orderKey=ORD-9|ts=10");
    assertThat(neworder.toPayload()).contains("ordType=MKT");
    assertThat(route.toPayload()).startsWith("MEOR|orderKey=ORD-9|ts=20");
    assertThat(route.toPayload()).contains("destination=ARCX").contains("routedOrderID=RTE-9");
  }

  @Test
  void nackedBatchKeepsEventsForRepairAndResubmission() {
    // CAT errors are repair-and-resubmit by next day, not drop.
    CatReporter reporter = new CatReporter(CatReporter.Wire.mock(false), "IMID-DEMO");
    reporter.newOrder("ORD-2", 1L, "AAPL", 1, 100L, null);

    CatReporter.BatchResult nacked = reporter.submitBatch();
    assertThat(nacked.acked()).isFalse();
    assertThat(nacked.errorCode()).isEqualTo("CAT-REJECT-MOCK");
    assertThat(reporter.pending()).hasSize(1);

    CatReporter fixed = new CatReporter(CatReporter.Wire.mock(true), "IMID-DEMO");
    fixed.newOrder("ORD-2", 1L, "AAPL", 1, 100L, null);
    assertThat(fixed.submitBatch().acked()).isTrue();
  }

  @Test
  void equityExecutionFlowsThroughTheReportingPipelineAsMeot() {
    // The 12.5 service path: US equity execution -> FINRA_CAT -> MEOT payload, acked.
    InMemoryRegulatoryReportingService service =
        new InMemoryRegulatoryReportingService(RegulatorDeterminer.crossAssetUs());
    service.registerProfile(ReportingProfile.mock(Regulator.FINRA_CAT));
    service.registerAdapter(CatMockAdapter.acking());

    ReportableTrade trade =
        new ReportableTrade(
            "TRD-77",
            "US_EQUITY",
            "US",
            "BBG000B9XRY4",
            1,
            500L,
            1_824_500L,
            Map.of("side", "1", "qty", "500", "price", "1824500",
                "orderId", "ORD-77", "eventTs", "123456"));
    List<RegReportEvent> events = service.report(trade, "fill");

    assertThat(events).isNotEmpty();
    RegReportEvent last = events.get(events.size() - 1);
    assertThat(last).isInstanceOf(RegReportEvent.RegReportAcked.class);

    // The MEOT payload links on the originating ORDER, not the trade ref.
    String payload = CatMockAdapter.acking().buildPayload(trade);
    assertThat(payload).startsWith("MEOT|orderKey=ORD-77|ts=123456");
    assertThat(payload).contains("tradeRef=TRD-77").contains("lastQty=500");
  }
}
