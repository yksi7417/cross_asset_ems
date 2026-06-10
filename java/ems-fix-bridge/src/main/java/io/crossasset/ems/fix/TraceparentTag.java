/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix;

import java.util.Optional;

/**
 * Codec for FIX tag {@code 9700 TraceparentHex} (task 8.3): a W3C {@code traceparent} header value,
 * hex-encoded so the dashes survive FIX engines that mangle non-alphanumeric payloads.
 *
 * <p>A {@code traceparent} is {@code 00-<32 hex traceId>-<16 hex spanId>-<2 hex flags>} (55 chars).
 * {@link #decodeTraceId} tolerates malformed input by returning empty — the caller falls back to
 * the session-minted trace per arch-observability.md.
 */
public final class TraceparentTag {

  /** Custom FIX tag carrying the hex-encoded W3C traceparent. */
  public static final int TAG = 9700;

  private static final int TRACEPARENT_LENGTH = 55;

  private TraceparentTag() {}

  /** Hex-encode a traceparent header value for the wire. */
  public static String encode(String traceparent) {
    StringBuilder sb = new StringBuilder(traceparent.length() * 2);
    for (int i = 0; i < traceparent.length(); i++) {
      sb.append(String.format("%02x", (int) traceparent.charAt(i)));
    }
    return sb.toString();
  }

  /**
   * Build a traceparent for an outbound hop: the chain's trace ID plus a deterministic span ID
   * derived from the ClOrdID (replay-safe; never zero), sampled flag set.
   */
  public static String traceparentFor(String traceId, String clOrdId) {
    long span = Math.max(1L, Integer.toUnsignedLong(clOrdId.hashCode()));
    return "00-" + traceId + "-" + String.format("%016x", span) + "-01";
  }

  /**
   * Decode a hex tag value back to the 32-hex-char W3C trace ID. Empty when the value is not
   * decodable hex, the decoded string is not traceparent-shaped, or the trace ID is invalid.
   */
  public static Optional<String> decodeTraceId(String hexValue) {
    if (hexValue == null || hexValue.length() != TRACEPARENT_LENGTH * 2) {
      return Optional.empty();
    }
    StringBuilder sb = new StringBuilder(TRACEPARENT_LENGTH);
    for (int i = 0; i < hexValue.length(); i += 2) {
      try {
        sb.append((char) Integer.parseInt(hexValue.substring(i, i + 2), 16));
      } catch (NumberFormatException e) {
        return Optional.empty();
      }
    }
    String traceparent = sb.toString();
    // 00-<32>-<16>-<2> with dashes at 2, 35, 52
    if (traceparent.charAt(2) != '-'
        || traceparent.charAt(35) != '-'
        || traceparent.charAt(52) != '-') {
      return Optional.empty();
    }
    String traceId = traceparent.substring(3, 35);
    return io.crossasset.ems.observability.trace.TracePropagator.isValidTraceId(traceId)
        ? Optional.of(traceId)
        : Optional.empty();
  }
}
