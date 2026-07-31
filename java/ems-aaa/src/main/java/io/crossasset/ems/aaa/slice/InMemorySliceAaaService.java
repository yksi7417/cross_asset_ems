/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa.slice;

import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Sessions registered from the journal, held in memory for the length of one run.
 *
 * <p>Backed by a {@link TreeMap}: its iteration order can reach the output journal, and the
 * conformance gate compares that journal byte-for-byte across three languages.
 *
 * <p>Not thread-safe. The slice binary is single-threaded by design.
 */
public final class InMemorySliceAaaService implements SliceAaaService {

  /** Catalog code: session ID unknown or already logged out. */
  public static final String CODE_SESSION_NOT_FOUND = "EMS-SES-1002";

  /** Catalog code: user does not hold the required permission tag. */
  public static final String CODE_MISSING_TAG = "EMS-PRM-1001";

  private final SortedMap<Long, SliceSession> sessions = new TreeMap<>();

  /**
   * Registers a session, replacing any existing one with the same identifier.
   *
   * <p>Replacement rather than rejection: a second logon on the same identifier is a re-logon, and
   * the FIX session layer that would police it is out of scope for this slice (ADR 0002).
   */
  public void register(SliceSession session) {
    sessions.put(session.sessionId(), session);
  }

  @Override
  public Optional<SliceSession> session(long sessionId) {
    return Optional.ofNullable(sessions.get(sessionId));
  }

  @Override
  public AuthorizationDecision authorize(SliceSession session, String tag) {
    if (tag.isEmpty()) {
      return AuthorizationDecision.ALLOW;
    }
    if (session.identity().holds(tag)) {
      return AuthorizationDecision.ALLOW;
    }
    return new AuthorizationDecision.Deny(
        CODE_MISSING_TAG,
        "PRM",
        "User " + session.identity().user() + " does not have permission tag #" + tag + ".");
  }
}
