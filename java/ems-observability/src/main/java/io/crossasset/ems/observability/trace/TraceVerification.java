/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.observability.trace;

import java.util.Set;

/**
 * The result of verifying a chain's trace continuity. {@code singleTraceId} is true when every
 * observed hop carried the same trace ID; {@code complete} additionally requires that all the
 * required hops were observed. A failed verification names the divergent trace IDs and the missing
 * hops for diagnosis.
 */
public record TraceVerification(
    boolean singleTraceId,
    String traceId,
    Set<TraceHop> observedHops,
    Set<TraceHop> missingHops,
    Set<String> distinctTraceIds) {

  public TraceVerification {
    observedHops = Set.copyOf(observedHops);
    missingHops = Set.copyOf(missingHops);
    distinctTraceIds = Set.copyOf(distinctTraceIds);
  }

  /** A single trace ID across all hops AND every required hop observed. */
  public boolean complete() {
    return singleTraceId && missingHops.isEmpty();
  }
}
