/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa;

import static org.junit.jupiter.api.Assertions.*;

import io.crossasset.ems.transport.session.SequenceRecoveryService;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for AAA + SequenceRecoveryService integration. Per arch-sequence-recovery.md and
 * entry-point-aaa.md.
 *
 * <p>Task 5.5 — Session sequence recovery integrated.
 */
class AaaServiceSequenceTest {

  private InMemoryAaaEventLog eventLog;
  private SequenceRecoveryService seqRecovery;
  private InMemoryAaaService service;

  @BeforeEach
  void setUp() {
    eventLog = new InMemoryAaaEventLog();
    seqRecovery = new SequenceRecoveryService();
    service = new InMemoryAaaService(eventLog, null, seqRecovery);
    service.registerCredential("tok-alice", "ACME", "EQ", "alice", Set.of());
  }

  // ── logon + sequence initialisation ─────────────────────────────────────

  @Test
  void logon_withDeclaredSeq1_sessionEstablished() {
    LogonOutcome outcome =
        service.logon(new LogonCredentials(CredentialKind.FIX_LOGON, "tok-alice", 1L));
    assertInstanceOf(LogonOutcome.Accepted.class, outcome);
  }

  @Test
  void logon_withDeclaredSeq_seqRecoveryInitialised() {
    LogonOutcome.Accepted accepted =
        assertInstanceOf(
            LogonOutcome.Accepted.class,
            service.logon(new LogonCredentials(CredentialKind.FIX_LOGON, "tok-alice", 1L)));
    long sessionId = accepted.session().sessionId();
    // Sequence recovery registered the session; first in-order message should pass
    Optional<String> result = service.checkIncoming(sessionId, 1L);
    assertTrue(result.isEmpty(), "First in-order message should be accepted");
  }

  // ── checkIncoming — in-order ─────────────────────────────────────────────

  @Test
  void checkIncoming_inOrderMessages_returnsEmpty() {
    LogonOutcome.Accepted accepted =
        assertInstanceOf(
            LogonOutcome.Accepted.class,
            service.logon(new LogonCredentials(CredentialKind.FIX_LOGON, "tok-alice", 1L)));
    long sessionId = accepted.session().sessionId();
    // Messages 1, 2, 3 in order
    assertTrue(service.checkIncoming(sessionId, 1L).isEmpty());
    assertTrue(service.checkIncoming(sessionId, 2L).isEmpty());
    assertTrue(service.checkIncoming(sessionId, 3L).isEmpty());
  }

  // ── checkIncoming — gap detected ─────────────────────────────────────────

  @Test
  void checkIncoming_sequenceGap_returnsResend() {
    LogonOutcome.Accepted accepted =
        assertInstanceOf(
            LogonOutcome.Accepted.class,
            service.logon(new LogonCredentials(CredentialKind.FIX_LOGON, "tok-alice", 1L)));
    long sessionId = accepted.session().sessionId();
    // Consume seq=1, then jump to seq=5 (gap: 2, 3, 4 missing)
    assertTrue(service.checkIncoming(sessionId, 1L).isEmpty());
    Optional<String> result = service.checkIncoming(sessionId, 5L);
    assertTrue(result.isPresent());
    assertEquals("RESEND", result.get());
  }

  // ── checkIncoming — session not found ────────────────────────────────────

  @Test
  void checkIncoming_unknownSession_returnsSessionNotFound() {
    Optional<String> result = service.checkIncoming(Long.MAX_VALUE, 1L);
    assertTrue(result.isPresent());
    assertEquals("SESSION_NOT_FOUND", result.get());
  }

  // ── checkIncoming — no seq recovery wired ────────────────────────────────

  @Test
  void checkIncoming_noSeqRecoveryWired_returnsEmpty() {
    InMemoryAaaService noSeqService = new InMemoryAaaService(eventLog);
    noSeqService.registerCredential("tok-bob", "ACME", "FX", "bob", Set.of());
    LogonOutcome.Accepted accepted =
        assertInstanceOf(
            LogonOutcome.Accepted.class,
            service.logon(new LogonCredentials(CredentialKind.TOKEN, "tok-alice", 1L)));
    // With no seq recovery, checkIncoming is always empty (passthrough)
    Optional<String> result = noSeqService.checkIncoming(accepted.session().sessionId(), 999L);
    assertTrue(result.isEmpty());
  }

  // ── LogonCredentials.fresh factory ───────────────────────────────────────

  @Test
  void fresh_factory_setsSeqTo1() {
    LogonCredentials creds = LogonCredentials.fresh(CredentialKind.TOKEN, "tok-alice");
    assertEquals(1L, creds.declaredSeq());
  }

  @Test
  void fresh_factory_logon_successfullyEstablishesSession() {
    LogonOutcome outcome = service.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "tok-alice"));
    assertInstanceOf(LogonOutcome.Accepted.class, outcome);
  }

  // ── Multiple concurrent sessions ────────────────────────────────────────

  @Test
  void twoSessions_independentSequenceTracking() {
    service.registerCredential("tok-bob", "ACME", "FX", "bob", Set.of());
    LogonOutcome.Accepted alice =
        assertInstanceOf(
            LogonOutcome.Accepted.class,
            service.logon(new LogonCredentials(CredentialKind.FIX_LOGON, "tok-alice", 1L)));
    LogonOutcome.Accepted bob =
        assertInstanceOf(
            LogonOutcome.Accepted.class,
            service.logon(new LogonCredentials(CredentialKind.FIX_LOGON, "tok-bob", 1L)));
    long aliceSessionId = alice.session().sessionId();
    long bobSessionId = bob.session().sessionId();

    // Alice: in-order
    assertTrue(service.checkIncoming(aliceSessionId, 1L).isEmpty());
    // Bob: gap
    Optional<String> bobGap = service.checkIncoming(bobSessionId, 5L);
    assertTrue(bobGap.isPresent());
    // Alice continues in-order, unaffected by Bob's gap
    assertTrue(service.checkIncoming(aliceSessionId, 2L).isEmpty());
  }
}
