/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa;

import java.util.Optional;

/**
 * Entry point for Authentication / Authorization / Accounting. Every inbound connection — REST,
 * FIX, automation — passes through here before reaching any business operation. Per
 * entry-point-aaa.md.
 *
 * <p>Task 5.1 — AAA service skeleton.
 */
public interface AaaService {

  /**
   * Authenticate the caller and establish a session.
   *
   * @return {@link LogonOutcome.Accepted} on success, {@link LogonOutcome.Rejected} on credential
   *     or policy failure.
   */
  LogonOutcome logon(LogonCredentials credentials);

  /** Terminate the session. No-op if session is unknown or already logged out. */
  void logout(long sessionId, String reason);

  /** Introspect an active session. Empty if the session is unknown or has been logged out. */
  Optional<Session> sessionInfo(long sessionId);
}
