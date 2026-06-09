/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the AAA service skeleton.
 *
 * <p>Task 5.1 — AAA service skeleton.
 */
class AaaServiceTest {

  private InMemoryAaaEventLog eventLog;
  private InMemoryAaaService service;

  @BeforeEach
  void setUp() {
    eventLog = new InMemoryAaaEventLog();
    service = new InMemoryAaaService(eventLog);
    service.registerCredential("tok-alice", "ACME", "EQ", "alice", Set.of("#trade-eq"));
    service.registerCredential("tok-bob", "ACME", "FX", "bob", Set.of("#trade-fx-spot"));
  }

  // ── Logon — success ──────────────────────────────────────────────────────

  @Test
  void logon_validToken_returnsAccepted() {
    LogonOutcome outcome = service.logon(new LogonCredentials(CredentialKind.TOKEN, "tok-alice"));
    assertInstanceOf(LogonOutcome.Accepted.class, outcome);
  }

  @Test
  void logon_accepted_sessionHasCorrectIdentity() {
    LogonOutcome.Accepted accepted =
        assertInstanceOf(
            LogonOutcome.Accepted.class,
            service.logon(new LogonCredentials(CredentialKind.TOKEN, "tok-alice")));
    Identity id = accepted.session().identity();
    assertEquals("alice", id.userId());
    assertEquals("ACME", id.firmId());
    assertEquals("EQ", id.deskId());
    assertTrue(id.tags().contains("#trade-eq"));
  }

  @Test
  void logon_accepted_sessionIdIsPositive() {
    LogonOutcome.Accepted accepted =
        assertInstanceOf(
            LogonOutcome.Accepted.class,
            service.logon(new LogonCredentials(CredentialKind.TOKEN, "tok-alice")));
    assertTrue(accepted.session().sessionId() > 0);
  }

  @Test
  void logon_success_emitsConnectAttemptedThenAuthenticated() {
    service.logon(new LogonCredentials(CredentialKind.TOKEN, "tok-alice"));
    List<AaaEvent> events = eventLog.events();
    assertEquals(2, events.size());
    assertInstanceOf(AaaEvent.ConnectAttempted.class, events.get(0));
    assertInstanceOf(AaaEvent.Authenticated.class, events.get(1));
  }

  @Test
  void logon_multipleCallsSameCredential_differentSessionIds() {
    LogonOutcome.Accepted a1 =
        assertInstanceOf(
            LogonOutcome.Accepted.class,
            service.logon(new LogonCredentials(CredentialKind.TOKEN, "tok-alice")));
    LogonOutcome.Accepted a2 =
        assertInstanceOf(
            LogonOutcome.Accepted.class,
            service.logon(new LogonCredentials(CredentialKind.TOKEN, "tok-alice")));
    assertNotEquals(a1.session().sessionId(), a2.session().sessionId());
  }

  // ── Logon — failure ──────────────────────────────────────────────────────

  @Test
  void logon_badToken_returnsRejected() {
    LogonOutcome outcome = service.logon(new LogonCredentials(CredentialKind.TOKEN, "bad-token"));
    assertInstanceOf(LogonOutcome.Rejected.class, outcome);
  }

  @Test
  void logon_badToken_rejectCodeIsSes1001() {
    LogonOutcome.Rejected rejected =
        assertInstanceOf(
            LogonOutcome.Rejected.class,
            service.logon(new LogonCredentials(CredentialKind.TOKEN, "bad-token")));
    assertEquals("EMS-SES-1001", rejected.rejectCode());
  }

  @Test
  void logon_badToken_emitsConnectAttemptedThenLogonRejected() {
    service.logon(new LogonCredentials(CredentialKind.TOKEN, "bad-token"));
    List<AaaEvent> events = eventLog.events();
    assertEquals(2, events.size());
    assertInstanceOf(AaaEvent.ConnectAttempted.class, events.get(0));
    AaaEvent.LogonRejected rejected = assertInstanceOf(AaaEvent.LogonRejected.class, events.get(1));
    assertEquals("EMS-SES-1001", rejected.rejectCode());
  }

  // ── session_info ──────────────────────────────────────────────────────────

  @Test
  void sessionInfo_afterLogon_returnsSession() {
    LogonOutcome.Accepted accepted =
        assertInstanceOf(
            LogonOutcome.Accepted.class,
            service.logon(new LogonCredentials(CredentialKind.TOKEN, "tok-alice")));
    long sessionId = accepted.session().sessionId();
    assertTrue(service.sessionInfo(sessionId).isPresent());
    assertEquals(sessionId, service.sessionInfo(sessionId).get().sessionId());
  }

  @Test
  void sessionInfo_unknownSessionId_returnsEmpty() {
    assertTrue(service.sessionInfo(Long.MAX_VALUE).isEmpty());
  }

  // ── logout ───────────────────────────────────────────────────────────────

  @Test
  void logout_removesActiveSession() {
    LogonOutcome.Accepted accepted =
        assertInstanceOf(
            LogonOutcome.Accepted.class,
            service.logon(new LogonCredentials(CredentialKind.TOKEN, "tok-alice")));
    long sessionId = accepted.session().sessionId();
    service.logout(sessionId, "user-requested");
    assertTrue(service.sessionInfo(sessionId).isEmpty());
  }

  @Test
  void logout_emitsSessionLogoutEvent() {
    LogonOutcome.Accepted accepted =
        assertInstanceOf(
            LogonOutcome.Accepted.class,
            service.logon(new LogonCredentials(CredentialKind.TOKEN, "tok-alice")));
    long sessionId = accepted.session().sessionId();
    service.logout(sessionId, "user-requested");
    List<AaaEvent> events = eventLog.events();
    AaaEvent.SessionLogout logout =
        (AaaEvent.SessionLogout)
            events.stream()
                .filter(e -> e instanceof AaaEvent.SessionLogout)
                .findFirst()
                .orElseThrow();
    assertEquals(sessionId, logout.sessionId());
    assertEquals("user-requested", logout.reason());
  }

  @Test
  void logout_unknownSession_isNoOp() {
    assertDoesNotThrow(() -> service.logout(Long.MAX_VALUE, "unknown"));
  }

  // ── identity effectiveTags skeleton behaviour ─────────────────────────────

  @Test
  void logon_skeletonEffectiveTagsEqualsTags() {
    LogonOutcome.Accepted accepted =
        assertInstanceOf(
            LogonOutcome.Accepted.class,
            service.logon(new LogonCredentials(CredentialKind.TOKEN, "tok-alice")));
    Identity id = accepted.session().identity();
    assertEquals(id.tags(), id.effectiveTags());
  }
}
