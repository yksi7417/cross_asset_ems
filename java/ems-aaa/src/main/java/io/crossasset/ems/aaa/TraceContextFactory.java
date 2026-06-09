/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Produces {@link TraceContext} instances — either minted fresh (at session-logon) or adopted from
 * an inbound W3C {@code traceparent} header. Per arch-observability.md and entry-point-aaa.md.
 *
 * <p>Task 5.4 — Trace ID stamping at session-logon.
 */
public final class TraceContextFactory {

  private TraceContextFactory() {}

  /**
   * Mints a fresh trace context: random UUID as trace ID, random parent span ID, sampled flag set.
   * Called at logon when no inbound trace header is present.
   */
  public static TraceContext mint() {
    UUID traceId = UUID.randomUUID();
    long spanId = ThreadLocalRandom.current().nextLong();
    return new TraceContext(
        traceId.getMostSignificantBits(),
        traceId.getLeastSignificantBits(),
        spanId,
        TraceContext.FLAG_SAMPLED);
  }

  /**
   * Adopts an inbound W3C {@code traceparent} header. Parses trace ID and flags from the header;
   * generates a fresh span ID to represent this hop.
   *
   * <p>Expected format: {@code 00-{32hex}-{16hex}-{2hex}}
   *
   * @throws IllegalArgumentException if the header is malformed
   */
  public static TraceContext adopt(String traceparent) {
    if (traceparent == null) throw new IllegalArgumentException("traceparent must not be null");
    String[] parts = traceparent.split("-", -1);
    if (parts.length != 4) {
      throw new IllegalArgumentException(
          "Invalid traceparent (expected 4 dash-delimited parts): " + traceparent);
    }
    if (!"00".equals(parts[0])) {
      throw new IllegalArgumentException(
          "Unsupported traceparent version (expected 00): " + parts[0]);
    }
    String traceIdHex = parts[1];
    String flagsHex = parts[3];
    if (traceIdHex.length() != 32) {
      throw new IllegalArgumentException(
          "Invalid trace_id length (expected 32 hex chars): " + traceIdHex);
    }
    if (flagsHex.length() != 2) {
      throw new IllegalArgumentException(
          "Invalid flags length (expected 2 hex chars): " + flagsHex);
    }
    long high = Long.parseUnsignedLong(traceIdHex.substring(0, 16), 16);
    long low = Long.parseUnsignedLong(traceIdHex.substring(16, 32), 16);
    byte flags = (byte) Integer.parseInt(flagsHex, 16);
    // Always generate a fresh span ID at this service boundary
    long spanId = ThreadLocalRandom.current().nextLong();
    return new TraceContext(high, low, spanId, flags);
  }
}
