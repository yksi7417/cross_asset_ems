/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tests for {@link TraceparentTag} — the 9700 TraceparentHex codec (task 8.3). */
class TraceparentTagTest {

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

  @Test
  void roundTrip_encodeThenDecode_recoversTraceId() {
    String traceparent = TraceparentTag.traceparentFor(TRACE_ID, "CL-1");
    String hex = TraceparentTag.encode(traceparent);
    assertThat(TraceparentTag.decodeTraceId(hex)).contains(TRACE_ID);
  }

  @Test
  void traceparentFor_isDeterministicAndWellFormed() {
    String a = TraceparentTag.traceparentFor(TRACE_ID, "CL-1");
    String b = TraceparentTag.traceparentFor(TRACE_ID, "CL-1");
    assertThat(a).isEqualTo(b).hasSize(55).startsWith("00-" + TRACE_ID + "-").endsWith("-01");
  }

  @Test
  void decode_garbageHex_returnsEmpty() {
    assertThat(TraceparentTag.decodeTraceId("zz")).isEmpty();
    assertThat(TraceparentTag.decodeTraceId(null)).isEmpty();
    assertThat(TraceparentTag.decodeTraceId("00".repeat(55))).isEmpty(); // all-zero trace id
  }

  @Test
  void decode_wrongShape_returnsEmpty() {
    // Correct length but not traceparent-shaped once decoded.
    String notTraceparent = TraceparentTag.encode("x".repeat(55));
    assertThat(TraceparentTag.decodeTraceId(notTraceparent)).isEmpty();
  }
}
