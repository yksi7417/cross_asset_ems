package io.crossasset.ems.transport.session;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
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
}
