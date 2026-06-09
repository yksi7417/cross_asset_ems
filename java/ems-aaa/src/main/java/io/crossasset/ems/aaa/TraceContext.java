/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa;

/**
 * W3C trace context carried in the AAA session. Maps to the SBE {@code SessionHeader.traceId} (16
 * bytes), {@code parentSpanId} (8 bytes), and {@code traceFlags} (1 byte) per
 * arch-sbe-aeron-transport.md and arch-observability.md.
 *
 * <p>Stored as two {@code long} halves for the 128-bit trace ID and one {@code long} for the 64-bit
 * parent span ID — avoids mutable {@code byte[]} in an immutable record.
 *
 * <p>{@code traceFlags}: bit 0 = W3C sampled; bit 1 = EMS audit-mandatory per SessionHeader.
 *
 * <p>Task 5.4 — Trace ID stamping at session-logon.
 */
public record TraceContext(long traceIdHigh, long traceIdLow, long parentSpanId, byte traceFlags) {

  /** W3C sampled flag (bit 0). */
  public static final byte FLAG_SAMPLED = 0x01;

  /** EMS audit-mandatory flag (bit 1) — always trace compliance flows. */
  public static final byte FLAG_AUDIT = 0x02;

  /** Returns {@code true} if the W3C sampled bit is set. */
  public boolean isSampled() {
    return (traceFlags & FLAG_SAMPLED) != 0;
  }

  /** Returns the W3C {@code traceparent} header value for propagation. */
  public String toTraceparent() {
    return "00-"
        + toHex16(traceIdHigh)
        + toHex16(traceIdLow)
        + "-"
        + toHex16(parentSpanId)
        + "-"
        + String.format("%02x", Byte.toUnsignedInt(traceFlags));
  }

  private static String toHex16(long value) {
    return String.format("%016x", value);
  }
}
