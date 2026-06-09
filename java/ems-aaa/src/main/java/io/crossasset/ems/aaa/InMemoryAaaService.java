/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory AAA service skeleton. Validates credentials against a pre-registered token store.
 *
 * <p>Identity resolution uses a flat token→identity registry. The Firm/Desk/User hierarchy (task
 * 5.2) and the tag-permission AND-gate (task 5.3) extend this service. Sequence recovery
 * integration follows in task 5.5.
 *
 * <p>Clock: uses wall time via {@code System.currentTimeMillis()}. Production code injects the
 * sim-clock per arch-time-replay-server.md.
 *
 * <p>Task 5.1 — AAA service skeleton.
 */
public final class InMemoryAaaService implements AaaService {

  private record CredentialEntry(String firmId, String deskId, String userId, Set<String> tags) {}

  private final Map<String, CredentialEntry> credentialStore = new ConcurrentHashMap<>();
  private final Map<Long, Session> activeSessions = new ConcurrentHashMap<>();
  private final AtomicLong sessionIdSeq = new AtomicLong(1);
  private final AaaEventLog eventLog;

  public InMemoryAaaService(AaaEventLog eventLog) {
    this.eventLog = Objects.requireNonNull(eventLog, "eventLog");
  }

  /**
   * Register a token credential for testing or bootstrap. Tags supplied here become both {@code
   * tags} and {@code effectiveTags} on the resulting Identity; task 5.3 wires in the AND-gate.
   */
  public void registerCredential(
      String token, String firmId, String deskId, String userId, Set<String> tags) {
    credentialStore.put(
        Objects.requireNonNull(token, "token"),
        new CredentialEntry(
            Objects.requireNonNull(firmId, "firmId"),
            Objects.requireNonNull(deskId, "deskId"),
            Objects.requireNonNull(userId, "userId"),
            Set.copyOf(Objects.requireNonNull(tags, "tags"))));
  }

  @Override
  public LogonOutcome logon(LogonCredentials credentials) {
    long nowMicros = System.currentTimeMillis() * 1_000L;
    eventLog.record(new AaaEvent.ConnectAttempted(credentials.kind(), nowMicros));

    CredentialEntry entry = credentialStore.get(credentials.token());
    if (entry == null) {
      String msg = "Logon failed: invalid credentials";
      eventLog.record(new AaaEvent.LogonRejected("EMS-SES-1001", msg, nowMicros));
      return new LogonOutcome.Rejected("EMS-SES-1001", msg);
    }

    long sessionId = sessionIdSeq.getAndIncrement();
    // effectiveTags equals tags in the skeleton; task 5.3 computes the AND-gate intersection
    Identity identity =
        new Identity(
            entry.firmId(),
            entry.deskId(),
            entry.userId(),
            credentials.token(),
            entry.tags(),
            entry.tags());
    Session session = new Session(sessionId, identity, nowMicros);
    activeSessions.put(sessionId, session);
    eventLog.record(new AaaEvent.Authenticated(sessionId, identity, nowMicros));
    return new LogonOutcome.Accepted(session);
  }

  @Override
  public void logout(long sessionId, String reason) {
    Session removed = activeSessions.remove(sessionId);
    if (removed != null) {
      long nowMicros = System.currentTimeMillis() * 1_000L;
      eventLog.record(new AaaEvent.SessionLogout(sessionId, reason, nowMicros));
    }
  }

  @Override
  public Optional<Session> sessionInfo(long sessionId) {
    return Optional.ofNullable(activeSessions.get(sessionId));
  }
}
