/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa;

import io.crossasset.ems.transport.session.SequenceRecoveryService;
import java.util.Optional;

/**
 * Entry point for Authentication / Authorization / Accounting. Every inbound connection — REST,
 * FIX, automation — passes through here before reaching any business operation. Per
 * entry-point-aaa.md.
 *
 * <p>Task 5.1 — AAA service skeleton. checkIncoming added in task 5.5.
 */
public interface AaaService {

  /**
   * Authenticate the caller and establish a session. For FIX callers, {@code
   * credentials.declaredSeq()} is the MsgSeqNum from the 35=A Logon; the service calls the
   * underlying {@link SequenceRecoveryService} to decide if the session needs gap recovery.
   *
   * @return {@link LogonOutcome.Accepted} on success, {@link LogonOutcome.Rejected} on credential
   *     or policy failure.
   */
  LogonOutcome logon(LogonCredentials credentials);

  /** Terminate the session. No-op if session is unknown or already logged out. */
  void logout(long sessionId, String reason);

  /** Introspect an active session. Empty if the session is unknown or has been logged out. */
  Optional<Session> sessionInfo(long sessionId);

  /**
   * Check an inbound message sequence number against the session's expected sequence. Returns an
   * empty optional if the message is in-order; returns a non-empty diagnostic string ("RESEND",
   * "SESSION_NOT_FOUND") if a gap or duplicate is detected.
   */
  Optional<String> checkIncoming(long sessionId, long seqNum);
}
