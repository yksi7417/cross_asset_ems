/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.observability.trace;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records the trace ID observed at each hop of an order's chain and verifies that one trace ID runs
 * unbroken from the FIX edge to the venue (task 13.5; the assertion the MVP smoke test 15.1 makes).
 *
 * <p>Each hop emits an observation {@code (hop, traceId)} keyed by the chain identity (the order's
 * initial ClOrdID). {@link #verify} then checks a single distinct trace ID and full hop coverage.
 */
public final class TraceVerifier {

  private final Map<String, List<Observation>> byChain = new ConcurrentHashMap<>();

  private record Observation(TraceHop hop, String traceId) {}

  /** Record the trace ID seen at {@code hop} for the order chain {@code chainKey}. */
  public void observe(String chainKey, TraceHop hop, String traceId) {
    byChain.computeIfAbsent(chainKey, k -> new ArrayList<>()).add(new Observation(hop, traceId));
  }

  /**
   * Verify a chain carried a single trace ID across all {@code requiredHops}. The {@code traceId}
   * in the result is the trace ID seen at {@code FIX_IN} (the chain origin) when present, else the
   * first observed.
   */
  public TraceVerification verify(String chainKey, Set<TraceHop> requiredHops) {
    List<Observation> observations = byChain.getOrDefault(chainKey, List.of());

    Set<String> distinctTraceIds = new HashSet<>();
    Set<TraceHop> observedHops = EnumSet.noneOf(TraceHop.class);
    String originTraceId = null;
    String firstTraceId = null;
    for (Observation o : observations) {
      distinctTraceIds.add(o.traceId());
      observedHops.add(o.hop());
      if (firstTraceId == null) {
        firstTraceId = o.traceId();
      }
      if (o.hop() == TraceHop.FIX_IN) {
        originTraceId = o.traceId();
      }
    }

    Set<TraceHop> missingHops = EnumSet.noneOf(TraceHop.class);
    missingHops.addAll(requiredHops);
    missingHops.removeAll(observedHops);

    boolean singleTraceId = distinctTraceIds.size() == 1;
    String traceId = originTraceId != null ? originTraceId : firstTraceId;
    return new TraceVerification(
        singleTraceId, traceId, observedHops, missingHops, distinctTraceIds);
  }
}
