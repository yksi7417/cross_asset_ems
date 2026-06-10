/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.observability.trace;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The bridge's local {@code clOrdID → trace context} rejoin map (per arch-fix-api-bridge.md §
 * Distributed tracing over FIX). The trace ID is minted/extracted at the FIX edge and stamped here;
 * every downstream hop and the venue response re-attach the same trace ID by ClOrdID — so trace
 * continuity survives venues that strip the W3C {@code traceparent} (tag 9700) from the wire.
 *
 * <p>Trace IDs are W3C 128-bit values rendered as 32 lower-case hex chars. The propagator stores
 * them as opaque strings, keeping this harness decoupled from the SBE/AAA trace-context type.
 */
public final class TracePropagator {

  private final ConcurrentHashMap<String, String> clOrdIdToTraceId = new ConcurrentHashMap<>();

  /** Stamp the trace ID for a ClOrdID at the FIX edge (first sight of the order). */
  public void stamp(String clOrdId, String traceId) {
    clOrdIdToTraceId.put(clOrdId, traceId);
  }

  /** The trace ID previously stamped for a ClOrdID, if any. */
  public Optional<String> lookup(String clOrdId) {
    return Optional.ofNullable(clOrdIdToTraceId.get(clOrdId));
  }

  /**
   * Carry the chain's trace ID across a ClOrdID transition (a 35=G replace mints a new ClOrdID but
   * stays on the same chain — see arch-identity-chaining). The new ClOrdID inherits the prior one's
   * trace ID. No-op if the prior ClOrdID was never stamped.
   */
  public void alias(String newClOrdId, String fromClOrdId) {
    String traceId = clOrdIdToTraceId.get(fromClOrdId);
    if (traceId != null) {
      clOrdIdToTraceId.put(newClOrdId, traceId);
    }
  }

  /** Whether a trace ID is well-formed (W3C: 32 hex chars, not all zero). */
  public static boolean isValidTraceId(String traceId) {
    if (traceId == null || traceId.length() != 32) {
      return false;
    }
    boolean allZero = true;
    for (int i = 0; i < 32; i++) {
      char c = traceId.charAt(i);
      boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
      if (!hex) {
        return false;
      }
      if (c != '0') {
        allZero = false;
      }
    }
    return !allZero;
  }
}
