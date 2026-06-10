/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.stp;

import io.crossasset.ems.posttrade.allocation.AllocationEvent;
import io.crossasset.ems.posttrade.allocation.AllocationEvent.AllocationAnomaly;
import io.crossasset.ems.posttrade.allocation.AllocationEvent.AllocationApplied;
import io.crossasset.ems.posttrade.allocation.AllocationEvent.AllocationDeferred;
import io.crossasset.ems.posttrade.allocation.AllocationService;
import io.crossasset.ems.posttrade.allocation.AllocationTemplate;
import io.crossasset.ems.posttrade.allocation.Fill;
import io.crossasset.ems.posttrade.stp.StageHandler.StageContext;
import io.crossasset.ems.posttrade.stp.StpEvent.StpFillIngested;
import io.crossasset.ems.posttrade.stp.StpEvent.StpPipelineComplete;
import io.crossasset.ems.posttrade.stp.StpEvent.StpPipelineStarted;
import io.crossasset.ems.posttrade.stp.StpEvent.StpStageAnomaly;
import io.crossasset.ems.posttrade.stp.StpEvent.StpStageComplete;
import io.crossasset.ems.posttrade.stp.StpEvent.StpStageRetryRequested;
import io.crossasset.ems.posttrade.stp.StpState.AllocationState;
import io.crossasset.ems.posttrade.stp.StpState.Overall;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link StpOrchestrator}. Deterministic: stages run in the profile's declared order,
 * each handler is pure, and no clock or randomness participates — so replay reproduces the same
 * event stream.
 */
public final class InMemoryStpOrchestrator implements StpOrchestrator {

  private final AllocationService allocationService;
  private final Map<Stage, StageHandler> handlers = new ConcurrentHashMap<>();
  private final Map<String, Trade> trades = new ConcurrentHashMap<>();

  public InMemoryStpOrchestrator(AllocationService allocationService) {
    this.allocationService = allocationService;
  }

  @Override
  public void register(Stage stage, StageHandler handler) {
    handlers.put(stage, handler);
  }

  @Override
  public StpResult ingest(Fill fill, AllocationTemplate template, StageProfile profile) {
    List<StpEvent> events = new ArrayList<>();
    Trade trade = new Trade(fill.fillId(), fill.orderId(), profile);
    trades.put(fill.fillId(), trade);

    events.add(new StpFillIngested(fill.fillId(), fill.orderId()));
    List<Stage> planned = new ArrayList<>();
    planned.add(Stage.ALLOCATION);
    planned.addAll(profile.downstreamStages());
    events.add(new StpPipelineStarted(fill.fillId(), List.copyOf(planned)));

    // ── Allocation stage ──
    List<AllocationEvent> allocationEvents = allocationService.allocate(fill, template);
    List<AllocationApplied> applied =
        allocationEvents.stream()
            .filter(e -> e instanceof AllocationApplied)
            .map(e -> (AllocationApplied) e)
            .toList();

    if (allocationEvents.stream().anyMatch(e -> e instanceof AllocationAnomaly)) {
      trade.allocation = AllocationState.ANOMALY;
      String reason =
          allocationEvents.stream()
              .filter(e -> e instanceof AllocationAnomaly)
              .map(e -> ((AllocationAnomaly) e).reason())
              .findFirst()
              .orElse("allocation anomaly");
      events.add(new StpStageAnomaly(fill.fillId(), Stage.ALLOCATION, reason, "alloc-ops"));
    } else if (allocationEvents.stream().anyMatch(e -> e instanceof AllocationDeferred)) {
      trade.allocation = AllocationState.DEFERRED;
      // Pipeline pauses until the template is supplied; downstream does not run.
    } else {
      trade.allocation = AllocationState.APPLIED;
      trade.allocations = applied;
      events.add(new StpStageComplete(fill.fillId(), Stage.ALLOCATION, StageOutcome.COMPLETE));
      runDownstream(trade, events);
    }

    finalizeOverall(trade, events);
    return new StpResult(snapshot(trade), List.copyOf(events), allocationEvents);
  }

  @Override
  public StpResult resumeStage(String fillId, Stage stage, String by, String reason) {
    Trade trade = trades.get(fillId);
    if (trade == null) {
      throw new IllegalStateException("Unknown fill " + fillId);
    }
    List<StpEvent> events = new ArrayList<>();
    events.add(new StpStageRetryRequested(fillId, stage, by, reason));
    if (stage != Stage.ALLOCATION && trade.profile.downstreamStages().contains(stage)) {
      runStage(trade, stage, events);
    }
    finalizeOverall(trade, events);
    return new StpResult(snapshot(trade), List.copyOf(events), List.of());
  }

  @Override
  public Optional<StpState> state(String fillId) {
    Trade trade = trades.get(fillId);
    return trade == null ? Optional.empty() : Optional.of(snapshot(trade));
  }

  // ── internals ────────────────────────────────────────────────────────────────

  private void runDownstream(Trade trade, List<StpEvent> events) {
    for (Stage stage : trade.profile.downstreamStages()) {
      runStage(trade, stage, events);
    }
  }

  private void runStage(Trade trade, Stage stage, List<StpEvent> events) {
    StageHandler handler = handlers.get(stage);
    StageOutcome outcome;
    if (handler == null) {
      outcome = StageOutcome.NOT_REQUIRED;
    } else {
      outcome = handler.handle(new StageContext(trade.fillId, trade.orderId, trade.allocations));
    }
    trade.stages.put(stage, outcome);
    if (outcome == StageOutcome.ANOMALY) {
      events.add(new StpStageAnomaly(trade.fillId, stage, stage + " stage failed", stage + "-ops"));
    } else {
      events.add(new StpStageComplete(trade.fillId, stage, outcome));
    }
  }

  private void finalizeOverall(Trade trade, List<StpEvent> events) {
    Overall previous = trade.overall;
    trade.overall = computeOverall(trade);
    if (trade.overall == Overall.COMPLETE && previous != Overall.COMPLETE) {
      events.add(new StpPipelineComplete(trade.fillId, "all stages complete"));
    }
  }

  private Overall computeOverall(Trade trade) {
    if (trade.allocation == AllocationState.ANOMALY) {
      return Overall.ANOMALY;
    }
    if (trade.allocation != AllocationState.APPLIED) {
      return Overall.IN_PROGRESS; // PENDING or DEFERRED
    }
    boolean anyAnomaly = trade.stages.values().stream().anyMatch(o -> o == StageOutcome.ANOMALY);
    if (anyAnomaly) {
      return Overall.ANOMALY;
    }
    boolean allTerminal =
        trade.profile.downstreamStages().stream()
            .allMatch(
                s -> {
                  StageOutcome o = trade.stages.get(s);
                  return o == StageOutcome.COMPLETE || o == StageOutcome.NOT_REQUIRED;
                });
    return allTerminal ? Overall.COMPLETE : Overall.IN_PROGRESS;
  }

  private StpState snapshot(Trade trade) {
    return new StpState(
        trade.fillId, trade.orderId, trade.allocation, new EnumMap<>(trade.stages), trade.overall);
  }

  /** Mutable per-trade pipeline state held by the orchestrator. */
  private static final class Trade {
    final String fillId;
    final String orderId;
    final StageProfile profile;
    AllocationState allocation = AllocationState.PENDING;
    Overall overall = Overall.IN_PROGRESS;
    List<AllocationApplied> allocations = List.of();
    final EnumMap<Stage, StageOutcome> stages = new EnumMap<>(Stage.class);

    Trade(String fillId, String orderId, StageProfile profile) {
      this.fillId = fillId;
      this.orderId = orderId;
      this.profile = profile;
    }
  }
}
