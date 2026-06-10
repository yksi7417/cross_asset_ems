/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.observability.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Task 13.5 — verifies a single trace ID is carried unbroken from the FIX edge to the venue, the
 * assertion the MVP smoke (15.1) makes. Drives a simulated FIX-in → venue-out chain through the
 * {@link TracePropagator} rejoin map and checks the result with the {@link TraceVerifier}.
 */
class TraceVerificationTest {

  private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
  private static final Set<TraceHop> FULL_CHAIN = EnumSet.allOf(TraceHop.class);

  /** Simulate one hop: read the trace from the rejoin map by ClOrdID and record the observation. */
  private static void hop(
      TracePropagator propagator, TraceVerifier verifier, String clOrdId, TraceHop hop) {
    String traceId = propagator.lookup(clOrdId).orElse("<<unstamped>>");
    verifier.observe(clOrdId, hop, traceId);
  }

  @Test
  void fullChain_carriesSingleTraceId_endToEnd() {
    TracePropagator propagator = new TracePropagator();
    TraceVerifier verifier = new TraceVerifier();
    String clOrdId = "CL001";

    // FIX edge mints + stamps the trace, then observes; downstream hops re-attach by ClOrdID.
    propagator.stamp(clOrdId, TRACE_ID);
    hop(propagator, verifier, clOrdId, TraceHop.FIX_IN);
    hop(propagator, verifier, clOrdId, TraceHop.VALIDATE);
    hop(propagator, verifier, clOrdId, TraceHop.STAGE);
    hop(propagator, verifier, clOrdId, TraceHop.ROUTE);
    // Venue strips tag 9700; the bridge re-attaches the trace from its rejoin map at venue-out.
    hop(propagator, verifier, clOrdId, TraceHop.VENUE_OUT);

    TraceVerification result = verifier.verify(clOrdId, FULL_CHAIN);
    assertThat(result.singleTraceId()).isTrue();
    assertThat(result.traceId()).isEqualTo(TRACE_ID);
    assertThat(result.missingHops()).isEmpty();
    assertThat(result.complete()).isTrue();
  }

  @Test
  void brokenHop_differentTraceId_isDetected() {
    TraceVerifier verifier = new TraceVerifier();
    String chain = "CL002";
    verifier.observe(chain, TraceHop.FIX_IN, TRACE_ID);
    verifier.observe(chain, TraceHop.STAGE, TRACE_ID);
    // A hop that lost the context and started a fresh trace — the failure 13.5 must catch.
    verifier.observe(chain, TraceHop.VENUE_OUT, "ffffffffffffffffffffffffffffffff");

    TraceVerification result = verifier.verify(chain, FULL_CHAIN);
    assertThat(result.singleTraceId()).isFalse();
    assertThat(result.distinctTraceIds()).hasSize(2);
    assertThat(result.complete()).isFalse();
  }

  @Test
  void missingHop_isReported() {
    TraceVerifier verifier = new TraceVerifier();
    String chain = "CL003";
    verifier.observe(chain, TraceHop.FIX_IN, TRACE_ID);
    verifier.observe(chain, TraceHop.VALIDATE, TRACE_ID);
    verifier.observe(chain, TraceHop.STAGE, TRACE_ID);
    verifier.observe(chain, TraceHop.VENUE_OUT, TRACE_ID); // ROUTE never observed

    TraceVerification result = verifier.verify(chain, FULL_CHAIN);
    assertThat(result.singleTraceId()).isTrue();
    assertThat(result.missingHops()).containsExactly(TraceHop.ROUTE);
    assertThat(result.complete()).isFalse();
  }

  @Test
  void replaceChain_aliasCarriesTraceAcrossNewClOrdId() {
    TracePropagator propagator = new TracePropagator();
    propagator.stamp("CL004", TRACE_ID);
    // 35=G replace mints CL004R but stays on the same chain.
    propagator.alias("CL004R", "CL004");
    assertThat(propagator.lookup("CL004R")).contains(TRACE_ID);
  }

  @Test
  void traceIdValidation() {
    assertThat(TracePropagator.isValidTraceId(TRACE_ID)).isTrue();
    assertThat(TracePropagator.isValidTraceId("00000000000000000000000000000000")).isFalse();
    assertThat(TracePropagator.isValidTraceId("tooshort")).isFalse();
    assertThat(TracePropagator.isValidTraceId("ZZf7651916cd43dd8448eb211c80319c")).isFalse();
  }
}
