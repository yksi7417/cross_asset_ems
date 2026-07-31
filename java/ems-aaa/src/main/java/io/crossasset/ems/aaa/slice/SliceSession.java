/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa.slice;

import java.util.Objects;

/**
 * An established session.
 *
 * <p>No {@code establishedAtMicros}: the production {@code Session} carries one, read from a clock,
 * and the slice cannot read a clock without giving up byte-identical replay. Logical ordering is
 * carried by the journal's sequence numbers instead.
 *
 * @param sessionId identifier the order references
 * @param identity who the session belongs to
 */
public record SliceSession(long sessionId, SliceIdentity identity) {

  public SliceSession {
    Objects.requireNonNull(identity, "identity");
    if (sessionId < 0) {
      throw new IllegalArgumentException("sessionId must not be negative: " + sessionId);
    }
  }
}
