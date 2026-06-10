/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory;

import io.crossasset.ems.posttrade.regulatory.RegReportEvent.RegReportDeferred;
import io.crossasset.ems.posttrade.regulatory.RegReportEvent.RegReportFailed;
import io.crossasset.ems.posttrade.stp.Stage;
import io.crossasset.ems.posttrade.stp.StageHandler;
import io.crossasset.ems.posttrade.stp.StageOutcome;
import java.util.List;
import java.util.function.Consumer;

/**
 * Bridges the regulatory reporting service into the STP pipeline as the {@link
 * Stage#REGULATORY_REPORTING} handler (per arch-stp-pipeline.md fan-out). Builds a {@link
 * ReportableTrade} from the fill's allocations, reports it to all applicable regulators (TRACE for
 * US corp bond via {@link TraceMockAdapter}), and maps the outcome:
 *
 * <ul>
 *   <li>no applicable regulator → {@link StageOutcome#NOT_REQUIRED};
 *   <li>any report failed or deferred → {@link StageOutcome#ANOMALY} (ops queue);
 *   <li>otherwise (all acked) → {@link StageOutcome#COMPLETE}.
 * </ul>
 */
public final class RegulatoryStageHandler implements StageHandler {

  /** Maps a fill's allocation context into the reportable trade. */
  @FunctionalInterface
  public interface ReportableTradeBuilder {
    ReportableTrade build(StageContext context);
  }

  private final RegulatoryReportingService service;
  private final ReportableTradeBuilder tradeBuilder;
  private final Consumer<RegReportEvent> sink;

  public RegulatoryStageHandler(
      RegulatoryReportingService service, ReportableTradeBuilder tradeBuilder) {
    this(service, tradeBuilder, event -> {});
  }

  public RegulatoryStageHandler(
      RegulatoryReportingService service,
      ReportableTradeBuilder tradeBuilder,
      Consumer<RegReportEvent> sink) {
    this.service = service;
    this.tradeBuilder = tradeBuilder;
    this.sink = sink;
  }

  @Override
  public StageOutcome handle(StageContext context) {
    ReportableTrade trade = tradeBuilder.build(context);
    List<RegReportEvent> events = service.report(trade, "AllocationApplied");
    events.forEach(sink);

    if (events.isEmpty()) {
      return StageOutcome.NOT_REQUIRED;
    }
    boolean unresolved =
        events.stream()
            .anyMatch(e -> e instanceof RegReportFailed || e instanceof RegReportDeferred);
    return unresolved ? StageOutcome.ANOMALY : StageOutcome.COMPLETE;
  }
}
