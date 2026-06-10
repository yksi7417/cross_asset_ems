/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.confirmation;

import io.crossasset.ems.posttrade.stp.Stage;
import io.crossasset.ems.posttrade.stp.StageHandler;
import io.crossasset.ems.posttrade.stp.StageOutcome;
import java.util.List;
import java.util.function.Consumer;

/**
 * Bridges the confirmation service into the STP pipeline as the {@link Stage#CONFIRMATION} handler
 * (per arch-stp-pipeline.md fan-out). Builds our canonical {@link TradeRecord} from the fill's
 * allocations, submits it for matching, and reports {@link StageOutcome#COMPLETE} when matched or
 * {@link StageOutcome#ANOMALY} (→ ops dispute queue) when not.
 */
public final class ConfirmationStageHandler implements StageHandler {

  /** Maps a fill's allocation context into the canonical trade record to confirm. */
  @FunctionalInterface
  public interface TradeRecordBuilder {
    TradeRecord build(StageContext context);
  }

  private final ConfirmationService service;
  private final ConfirmationNetwork network;
  private final MatchTolerance tolerance;
  private final TradeRecordBuilder recordBuilder;
  private final Consumer<ConfirmationEvent> sink;

  public ConfirmationStageHandler(
      ConfirmationService service,
      ConfirmationNetwork network,
      MatchTolerance tolerance,
      TradeRecordBuilder recordBuilder) {
    this(service, network, tolerance, recordBuilder, event -> {});
  }

  public ConfirmationStageHandler(
      ConfirmationService service,
      ConfirmationNetwork network,
      MatchTolerance tolerance,
      TradeRecordBuilder recordBuilder,
      Consumer<ConfirmationEvent> sink) {
    this.service = service;
    this.network = network;
    this.tolerance = tolerance;
    this.recordBuilder = recordBuilder;
    this.sink = sink;
  }

  @Override
  public StageOutcome handle(StageContext context) {
    TradeRecord ours = recordBuilder.build(context);
    String confirmationId = "CONF-" + context.fillId();
    List<ConfirmationEvent> events = service.submit(confirmationId, ours, tolerance, network);
    events.forEach(sink);
    return service.stateOf(confirmationId).orElse(ConfirmationState.UNMATCHED)
            == ConfirmationState.MATCHED
        ? StageOutcome.COMPLETE
        : StageOutcome.ANOMALY;
  }
}
