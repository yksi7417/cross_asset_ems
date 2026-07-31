/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa;

import io.crossasset.ems.aaa.permission.TagPermissionEvaluator;
import io.crossasset.ems.core.clock.SystemTimeSource;
import io.crossasset.ems.core.clock.TimeSource;
import io.crossasset.ems.transport.session.SequenceRecoveryService;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory AAA service skeleton. Validates credentials against a pre-registered token store.
 *
 * <p>Identity resolution uses a flat token→identity registry. When a {@link TagPermissionEvaluator}
 * is supplied (task 5.3), {@code effectiveTags} is the AND-gated intersection; otherwise it equals
 * {@code tags}. When a {@link SequenceRecoveryService} is supplied (task 5.5), sequence state is
 * tracked per session.
 *
 * <p>Time comes from an injected {@link TimeSource}, defaulting to {@link
 * SystemTimeSource#INSTANCE}. Pass a {@link io.crossasset.ems.core.clock.FixedTimeSource} or a
 * {@link io.crossasset.ems.core.clock.SimulatedClock} to make session timestamps deterministic —
 * which is what replay and the polyglot conformance gate need. Until this was wired, the javadoc
 * claimed injection while the code read the wall clock directly.
 *
 * <p>Task 5.1 — AAA service skeleton. Extended in tasks 5.3, 5.4, 5.5.
 */
public final class InMemoryAaaService implements AaaService {

  private record CredentialEntry(String firmId, String deskId, String userId, Set<String> tags) {}

  private final Map<String, CredentialEntry> credentialStore = new ConcurrentHashMap<>();
  private final Map<Long, Session> activeSessions = new ConcurrentHashMap<>();
  private final AtomicLong sessionIdSeq = new AtomicLong(1);
  private final TimeSource timeSource;
  private final AaaEventLog eventLog;
  private final TagPermissionEvaluator tagPermissionEvaluator;
  private final SequenceRecoveryService seqRecovery;

  /** Skeleton constructor — no tag-permission AND-gate, no sequence recovery. */
  public InMemoryAaaService(AaaEventLog eventLog) {
    this(eventLog, null, null);
  }

  /** Constructor with tag-permission AND-gate. */
  public InMemoryAaaService(AaaEventLog eventLog, TagPermissionEvaluator tagPermissionEvaluator) {
    this(eventLog, tagPermissionEvaluator, null);
  }

  /** Full constructor with tag-permission AND-gate and sequence recovery (task 5.5). */
  public InMemoryAaaService(
      AaaEventLog eventLog,
      TagPermissionEvaluator tagPermissionEvaluator,
      SequenceRecoveryService seqRecovery) {
    this(eventLog, tagPermissionEvaluator, seqRecovery, SystemTimeSource.INSTANCE);
  }

  /**
   * Full constructor.
   *
   * @param timeSource where session timestamps come from. Inject a deterministic one for replay and
   *     for tests that assert on timing; the other constructors default to wall time.
   */
  public InMemoryAaaService(
      AaaEventLog eventLog,
      TagPermissionEvaluator tagPermissionEvaluator,
      SequenceRecoveryService seqRecovery,
      TimeSource timeSource) {
    this.timeSource = Objects.requireNonNull(timeSource, "timeSource");
    this.eventLog = Objects.requireNonNull(eventLog, "eventLog");
    this.tagPermissionEvaluator = tagPermissionEvaluator; // nullable
    this.seqRecovery = seqRecovery; // nullable
  }

  /** Register a token credential for testing or bootstrap. */
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

  /**
   * Establishes a session directly, bypassing credential authentication.
   *
   * <p>For callers that authenticate elsewhere and arrive with an identity already resolved — the
   * deterministic slice replays {@code SessionLogon} events off a journal, and a replay
   * re-authenticating against a credential store would be re-deciding a decision the journal
   * already records.
   *
   * <p>An existing session with the same id is <strong>replaced</strong>. A second logon on one id
   * is a re-logon; policing it belongs to the FIX session layer.
   *
   * @param sessionId the identifier callers will use to look this session up
   * @param identity the resolved identity, including its granted tags
   * @param traceContext the trace context to stamp. Supplied rather than minted because {@link
   *     TraceContextFactory#mint()} draws from {@code UUID.randomUUID()} and {@code
   *     ThreadLocalRandom}, and a replayed session must produce the same bytes every run.
   * @return the established session
   */
  public Session registerSession(long sessionId, Identity identity, TraceContext traceContext) {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(traceContext, "traceContext");
    if (sessionId < 0) {
      throw new IllegalArgumentException("sessionId must not be negative: " + sessionId);
    }
    Session session = new Session(sessionId, identity, timeSource.nowMicros(), traceContext);
    activeSessions.put(sessionId, session);
    // Keep the generator ahead of any externally supplied id, so a later logon()
    // cannot mint an id that collides with one a caller already registered.
    sessionIdSeq.updateAndGet(next -> Math.max(next, sessionId + 1));
    // Authenticated, not ConnectAttempted: the credential check happened
    // upstream, and the audit log should say a session was established rather
    // than imply this service validated anything.
    eventLog.record(new AaaEvent.Authenticated(sessionId, identity, timeSource.nowMicros()));
    return session;
  }

  /**
   * Establishes a session with a freshly minted trace context.
   *
   * <p>Convenience for live callers. Deterministic callers must use {@link #registerSession(long,
   * Identity, TraceContext)} — this one draws from a random source.
   */
  public Session registerSession(long sessionId, Identity identity) {
    return registerSession(sessionId, identity, TraceContextFactory.mint());
  }

  /** Remove a credential (SCIM deprovisioning, 18.9). Existing sessions end via logout. */
  public void removeCredential(String token) {
    credentialStore.remove(Objects.requireNonNull(token, "token"));
  }

  @Override
  public LogonOutcome logon(LogonCredentials credentials) {
    long nowMicros = timeSource.nowMicros();
    eventLog.record(new AaaEvent.ConnectAttempted(credentials.kind(), nowMicros));

    CredentialEntry entry = credentialStore.get(credentials.token());
    if (entry == null) {
      String msg = "Logon failed: invalid credentials";
      eventLog.record(new AaaEvent.LogonRejected("EMS-SES-1001", msg, nowMicros));
      return new LogonOutcome.Rejected("EMS-SES-1001", msg);
    }

    long sessionId = sessionIdSeq.getAndIncrement();
    // Build identity with tags; compute effectiveTags via the AND-gate if evaluator is present
    Identity identityForGating =
        new Identity(
            entry.firmId(),
            entry.deskId(),
            entry.userId(),
            credentials.token(),
            entry.tags(),
            entry.tags());
    Set<String> effectiveTags =
        tagPermissionEvaluator != null
            ? tagPermissionEvaluator.computeEffectiveTags(identityForGating)
            : entry.tags();
    Identity identity =
        new Identity(
            entry.firmId(),
            entry.deskId(),
            entry.userId(),
            credentials.token(),
            entry.tags(),
            effectiveTags);
    // Sequence recovery: initialize or resume session seq state (task 5.5)
    if (seqRecovery != null) {
      seqRecovery.logon(sessionId, credentials.declaredSeq());
    }
    TraceContext traceContext = TraceContextFactory.mint();
    Session session = new Session(sessionId, identity, nowMicros, traceContext);
    activeSessions.put(sessionId, session);
    eventLog.record(new AaaEvent.Authenticated(sessionId, identity, nowMicros));
    return new LogonOutcome.Accepted(session);
  }

  @Override
  public void logout(long sessionId, String reason) {
    Session removed = activeSessions.remove(sessionId);
    if (removed != null) {
      long nowMicros = timeSource.nowMicros();
      eventLog.record(new AaaEvent.SessionLogout(sessionId, reason, nowMicros));
    }
  }

  @Override
  public Optional<Session> sessionInfo(long sessionId) {
    return Optional.ofNullable(activeSessions.get(sessionId));
  }

  @Override
  public Optional<String> checkIncoming(long sessionId, long seqNum) {
    if (seqRecovery == null) return Optional.empty();
    return seqRecovery.checkSequence(sessionId, seqNum);
  }
}
