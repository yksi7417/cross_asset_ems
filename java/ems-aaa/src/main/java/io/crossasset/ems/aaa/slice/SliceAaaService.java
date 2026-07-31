/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa.slice;

import java.util.Optional;

/** Session lookup and the entitlement decision. */
public interface SliceAaaService {

  /** Returns the session, or empty when the identifier is unknown or has been logged out. */
  Optional<SliceSession> session(long sessionId);

  /**
   * Decides whether {@code session} may act with {@code tag}.
   *
   * <p>A null or empty tag means the action requires no entitlement and is allowed.
   */
  AuthorizationDecision authorize(SliceSession session, String tag);
}
