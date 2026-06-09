/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for TraceContext and TraceContextFactory. Per arch-observability.md.
 *
 * <p>Task 5.4 — Trace ID stamping at session-logon.
 */
class TraceContextTest {

  // ── mint ──────────────────────────────────────────────────────────────────

  @Test
  void mint_returnsNonZeroTraceId() {
    TraceContext ctx = TraceContextFactory.mint();
    assertTrue(ctx.traceIdHigh() != 0 || ctx.traceIdLow() != 0);
  }

  @Test
  void mint_setsW3cSampledFlag() {
    TraceContext ctx = TraceContextFactory.mint();
    assertTrue(ctx.isSampled());
  }

  @Test
  void mint_successiveCalls_differentTraceIds() {
    Set<Long> highBits = new HashSet<>();
    Set<Long> lowBits = new HashSet<>();
    for (int i = 0; i < 20; i++) {
      TraceContext ctx = TraceContextFactory.mint();
      highBits.add(ctx.traceIdHigh());
      lowBits.add(ctx.traceIdLow());
    }
    // With random UUIDs, all 20 should be distinct (probability of collision is negligible)
    assertTrue(
        highBits.size() > 1 || lowBits.size() > 1,
        "Successive mints should produce distinct trace IDs");
  }

  // ── toTraceparent ─────────────────────────────────────────────────────────

  @Test
  void toTraceparent_formatIs00_traceId_parentSpanId_flags() {
    // Use fixed values for deterministic format check
    TraceContext ctx =
        new TraceContext(
            0x0102030405060708L, 0x090a0b0c0d0e0f10L, 0x1112131415161718L, (byte) 0x01);
    String tp = ctx.toTraceparent();
    assertEquals("00-0102030405060708090a0b0c0d0e0f10-1112131415161718-01", tp);
  }

  @Test
  void toTraceparent_zeroTrace_producesZeroPaddedHex() {
    TraceContext ctx = new TraceContext(0L, 0L, 0L, (byte) 0x00);
    String tp = ctx.toTraceparent();
    assertEquals("00-00000000000000000000000000000000-0000000000000000-00", tp);
  }

  // ── adopt ─────────────────────────────────────────────────────────────────

  @Test
  void adopt_validTraceparent_preservesTraceId() {
    String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    TraceContext ctx = TraceContextFactory.adopt(traceparent);
    assertEquals(0x4bf92f3577b34da6L, ctx.traceIdHigh());
    assertEquals(0xa3ce929d0e0e4736L, ctx.traceIdLow());
    assertEquals((byte) 0x01, ctx.traceFlags());
  }

  @Test
  void adopt_validTraceparent_generatesNewSpanId() {
    String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    long originalSpanHex = 0x00f067aa0ba902b7L;
    // Adopted span ID is newly generated, so statistically different
    Set<Long> spanIds = new HashSet<>();
    for (int i = 0; i < 10; i++) {
      spanIds.add(TraceContextFactory.adopt(traceparent).parentSpanId());
    }
    // Should produce distinct new span IDs (not copy the inbound span)
    assertFalse(
        spanIds.stream().allMatch(id -> id == originalSpanHex),
        "adopt should generate a new span ID at this service boundary");
  }

  @Test
  void adopt_unsampledFlags_preservedInContext() {
    String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00";
    TraceContext ctx = TraceContextFactory.adopt(traceparent);
    assertFalse(ctx.isSampled());
  }

  @Test
  void adopt_nullTraceparent_throws() {
    assertThrows(IllegalArgumentException.class, () -> TraceContextFactory.adopt(null));
  }

  @Test
  void adopt_wrongNumberOfParts_throws() {
    assertThrows(IllegalArgumentException.class, () -> TraceContextFactory.adopt("00-abc-01"));
  }

  @Test
  void adopt_shortTraceId_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> TraceContextFactory.adopt("00-abc-00f067aa0ba902b7-01"));
  }

  @Test
  void adopt_unsupportedVersion_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> TraceContextFactory.adopt("ff-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"));
  }

  // ── round-trip ────────────────────────────────────────────────────────────

  @Test
  void mintedContext_toTraceparent_adoptable() {
    TraceContext original = TraceContextFactory.mint();
    String tp = original.toTraceparent();
    TraceContext adopted = TraceContextFactory.adopt(tp);
    assertEquals(original.traceIdHigh(), adopted.traceIdHigh());
    assertEquals(original.traceIdLow(), adopted.traceIdLow());
    assertEquals(original.traceFlags(), adopted.traceFlags());
  }

  // ── session-logon integration ─────────────────────────────────────────────

  @Test
  void logon_sessionHasNonNullTraceContext() {
    InMemoryAaaEventLog log = new InMemoryAaaEventLog();
    InMemoryAaaService svc = new InMemoryAaaService(log);
    svc.registerCredential("tok-alice", "F1", "D1", "alice", java.util.Set.of());
    LogonOutcome.Accepted accepted =
        assertInstanceOf(
            LogonOutcome.Accepted.class,
            svc.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "tok-alice")));
    assertNotNull(accepted.session().traceContext());
  }

  @Test
  void logon_differentSessions_differentTraceIds() {
    InMemoryAaaEventLog log = new InMemoryAaaEventLog();
    InMemoryAaaService svc = new InMemoryAaaService(log);
    svc.registerCredential("tok-alice", "F1", "D1", "alice", java.util.Set.of());
    LogonOutcome.Accepted a1 =
        assertInstanceOf(
            LogonOutcome.Accepted.class,
            svc.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "tok-alice")));
    LogonOutcome.Accepted a2 =
        assertInstanceOf(
            LogonOutcome.Accepted.class,
            svc.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "tok-alice")));
    // Different trace IDs for different sessions
    assertFalse(
        a1.session().traceContext().traceIdHigh() == a2.session().traceContext().traceIdHigh()
            && a1.session().traceContext().traceIdLow() == a2.session().traceContext().traceIdLow(),
        "Different logons should produce different trace IDs");
  }

  @Test
  void logon_sessionTraceContext_isSampled() {
    InMemoryAaaEventLog log = new InMemoryAaaEventLog();
    InMemoryAaaService svc = new InMemoryAaaService(log);
    svc.registerCredential("tok-alice", "F1", "D1", "alice", java.util.Set.of());
    LogonOutcome.Accepted accepted =
        assertInstanceOf(
            LogonOutcome.Accepted.class,
            svc.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "tok-alice")));
    assertTrue(accepted.session().traceContext().isSampled());
  }
}
