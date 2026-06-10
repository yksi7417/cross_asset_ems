package io.crossasset.ems.transport.session;

import static org.junit.jupiter.api.Assertions.*;

import io.crossasset.ems.transport.session.SequenceRecoveryService.BufferedMessage;
import io.crossasset.ems.transport.session.SequenceRecoveryService.HeartbeatAction;
import io.crossasset.ems.transport.session.SequenceRecoveryService.SessionStatus;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SequenceRecoveryServiceTest {
  private SequenceRecoveryService service;

  @BeforeEach
  void setUp() {
    service = new SequenceRecoveryService();
  }

  @Test
  void testNewSessionLogon() {
    var result = service.logon(1L, 100L);
    assertTrue(result.success());
    assertEquals(100L, result.expectedSeq());
  }

  @Test
  void testLogonSequenceTooLow() {
    service.logon(1L, 100L);
    var result = service.logon(1L, 90L);
    assertFalse(result.success());
    assertEquals(100L, result.expectedSeq());
    assertTrue(result.message().contains("EMS-SES-1002"));
  }

  @Test
  void testLogonSequenceGap() {
    service.logon(1L, 100L);
    var result = service.logon(1L, 110L);
    assertFalse(result.success());
    assertEquals(100L, result.expectedSeq());
    assertTrue(result.message().contains("EMS-SES-2001"));
  }

  @Test
  void testSequenceCheckSuccess() {
    service.logon(1L, 100L);
    Optional<String> gap = service.checkSequence(1L, 100L);
    assertTrue(gap.isEmpty());
  }

  @Test
  void testSequenceCheckGap() {
    service.logon(1L, 100L);
    Optional<String> gap = service.checkSequence(1L, 105L);
    assertTrue(gap.isPresent());
    assertEquals("RESEND", gap.get());
  }

  @Test
  void testSequenceCheckDuplicate() {
    service.logon(1L, 100L);
    service.checkSequence(1L, 100L); // inc to 101
    Optional<String> gap = service.checkSequence(1L, 100L);
    assertTrue(gap.isEmpty()); // duplicate should not trigger resend
  }

  @Test
  void testLogonSessionResumed() {
    service.logon(2L, 50L);
    var result = service.logon(2L, 50L);
    assertTrue(result.success());
    assertEquals(50L, result.expectedSeq());
    assertTrue(result.message().contains("resumed"));
  }

  @Test
  void testGetSessionExists() {
    service.logon(3L, 200L);
    var state = service.getSession(3L);
    assertTrue(state.isPresent());
    assertEquals(3L, state.get().sessionId());
  }

  @Test
  void testGetSessionNotFound() {
    var state = service.getSession(999L);
    assertTrue(state.isEmpty());
  }

  @Test
  void testCheckSequenceSessionNotFound() {
    Optional<String> result = service.checkSequence(404L, 1L);
    assertTrue(result.isPresent());
    assertEquals("SESSION_NOT_FOUND", result.get());
  }

  // ── inbound dedup: gap does not corrupt the expected counter (the skeleton's bug) ──

  @Test
  void gapThenResend_recoversInOrder() {
    service.logon(1L, 1L);
    assertTrue(service.checkSequence(1L, 1L).isEmpty()); // expected → 2
    // Jump to 5 — gap. Must NOT advance expected, or the resent 2,3,4 would look like duplicates.
    assertEquals("RESEND", service.checkSequence(1L, 5L).orElseThrow());
    // Peer resends the missing window in order; all must be accepted.
    assertTrue(service.checkSequence(1L, 2L).isEmpty());
    assertTrue(service.checkSequence(1L, 3L).isEmpty());
    assertTrue(service.checkSequence(1L, 4L).isEmpty());
    assertTrue(service.checkSequence(1L, 5L).isEmpty());
    // Gap closed → back to ACTIVE.
    assertEquals(SessionStatus.ACTIVE, service.getSession(1L).orElseThrow().status());
    assertEquals(6L, service.getSession(1L).orElseThrow().nextExpectedSeq());
  }

  @Test
  void duplicate_doesNotAdvanceExpected() {
    service.logon(1L, 1L);
    service.checkSequence(1L, 1L); // expected → 2
    service.checkSequence(1L, 1L); // duplicate — must not advance
    assertEquals(2L, service.getSession(1L).orElseThrow().nextExpectedSeq());
    assertTrue(service.checkSequence(1L, 2L).isEmpty()); // next in-order still accepted
  }

  @Test
  void gapStaysOpenUntilHighWaterReached() {
    service.logon(1L, 1L);
    service.checkSequence(1L, 1L);
    service.checkSequence(1L, 5L); // gap to 5
    assertEquals(SessionStatus.GAP_DETECTED, service.getSession(1L).orElseThrow().status());
    service.checkSequence(1L, 2L); // still recovering
    assertEquals(SessionStatus.GAP_DETECTED, service.getSession(1L).orElseThrow().status());
  }

  // ── outbound resend buffer ──────────────────────────────────────────────────

  @Test
  void recordOutbound_assignsMonotonicSeqFromOne() {
    service.logon(1L, 1L);
    assertEquals(1L, service.recordOutbound(1L, bytes("a")));
    assertEquals(2L, service.recordOutbound(1L, bytes("b")));
    assertEquals(3L, service.recordOutbound(1L, bytes("c")));
    assertEquals(4L, service.getSession(1L).orElseThrow().outboundSeq());
  }

  @Test
  void recordOutbound_unknownSession_throws() {
    assertThrows(IllegalStateException.class, () -> service.recordOutbound(99L, bytes("x")));
  }

  @Test
  void resend_returnsBufferedRange() {
    service.logon(1L, 1L);
    service.recordOutbound(1L, bytes("m1"));
    service.recordOutbound(1L, bytes("m2"));
    service.recordOutbound(1L, bytes("m3"));
    List<BufferedMessage> slice = service.resend(1L, 2L, 3L);
    assertEquals(2, slice.size());
    assertEquals(2L, slice.get(0).seq());
    assertArrayEquals(bytes("m2"), slice.get(0).payload());
    assertEquals(3L, slice.get(1).seq());
  }

  @Test
  void resumeOutbound_replaysFromSeq() {
    service.logon(1L, 1L);
    for (int i = 0; i < 5; i++) {
      service.recordOutbound(1L, bytes("m" + i));
    }
    // Peer reconnects declaring it last saw seq 2; replay 3,4,5.
    List<BufferedMessage> replay = service.resumeOutbound(1L, 3L);
    assertEquals(3, replay.size());
    assertEquals(3L, replay.get(0).seq());
    assertEquals(5L, replay.get(2).seq());
  }

  @Test
  void recordOutbound_evictsBeyondWindow() {
    var small = new SequenceRecoveryService(System::currentTimeMillis, 30_000L, 3);
    small.logon(1L, 1L);
    for (int i = 0; i < 5; i++) {
      small.recordOutbound(1L, bytes("m" + i)); // seqs 1..5, window keeps last 3 (3,4,5)
    }
    assertTrue(small.resend(1L, 1L, 2L).isEmpty(), "evicted seqs are unrecoverable");
    List<BufferedMessage> kept = small.resend(1L, 1L, 5L);
    assertEquals(3, kept.size());
    assertEquals(3L, kept.get(0).seq());
  }

  // ── heartbeats (injected clock) ─────────────────────────────────────────────

  @Test
  void checkLiveness_escalatesTestRequestThenStale() {
    AtomicLong now = new AtomicLong(0L);
    var svc = new SequenceRecoveryService(now::get, 1_000L, 100);
    svc.logon(1L, 1L); // lastActivity = 0

    now.set(500L);
    assertEquals(HeartbeatAction.OK, svc.checkLiveness(1L)); // within interval

    now.set(1_000L);
    assertEquals(HeartbeatAction.SEND_TEST_REQUEST, svc.checkLiveness(1L)); // one interval late

    now.set(2_000L);
    assertEquals(HeartbeatAction.STALE, svc.checkLiveness(1L)); // two intervals → stale
    assertEquals(SessionStatus.STALE, svc.getSession(1L).orElseThrow().status());
  }

  @Test
  void recordActivity_resetsLivenessAndClearsStale() {
    AtomicLong now = new AtomicLong(0L);
    var svc = new SequenceRecoveryService(now::get, 1_000L, 100);
    svc.logon(1L, 1L);

    now.set(2_000L);
    assertEquals(HeartbeatAction.STALE, svc.checkLiveness(1L));

    svc.recordActivity(1L); // peer spoke again at t=2000
    assertEquals(HeartbeatAction.OK, svc.checkLiveness(1L));
    assertEquals(SessionStatus.ACTIVE, svc.getSession(1L).orElseThrow().status());
  }

  @Test
  void checkLiveness_unknownSession_isStale() {
    assertEquals(HeartbeatAction.STALE, service.checkLiveness(404L));
  }

  private static byte[] bytes(String s) {
    return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }
}
