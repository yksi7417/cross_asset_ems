/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.transport.projection;

import io.crossasset.ems.transport.log.StreamId;

/**
 * A pure read-side projection that folds a stream of {@link LogRecord}s into a state value.
 *
 * <p>Implementations must be stateless: all state is passed through {@link #apply} — the same event
 * applied to the same state must always produce the same result. This purity enables deterministic
 * rebuild-from-scratch.
 *
 * <p>Idempotency is enforced by {@link ProjectionRunner}, not by this interface. The projection
 * itself is unaware of duplicate delivery.
 *
 * <p>NOTE: idempotency is keyed by {@code globalSeq} (monotonically increasing log position) rather
 * than a UUID {@code event_id} as specified in arch-projection-engine.md. This simplification is
 * valid as long as records are delivered in order; out-of-order re-delivery of an earlier seq would
 * be silently dropped. TODO: promote to full UUID-keyed idempotency when the event envelope lands.
 *
 * <p>Task 3.4 — Projection framework.
 *
 * @param <S> the immutable state type produced by this projection
 */
public interface Projection<S> {

  /** Stable identifier used as the snapshot key. Must not change once deployed. */
  String name();

  /**
   * Schema version of this projection. Increment when the reducer semantics change and a rebuild is
   * required to invalidate old snapshots.
   */
  int version();

  /** The initial (empty) state — returned on rebuild of an empty log. */
  S initialState();

  /**
   * Returns {@code true} if this projection should process events on {@code streamId}. Called
   * before {@link #apply}; rejected records are skipped by {@link ProjectionRunner}.
   */
  boolean accepts(StreamId streamId);

  /**
   * Pure reducer: given the current state and an accepted {@link LogRecord}, returns the next
   * state. Must not mutate {@code state} in place.
   *
   * @param state current projection state (never {@code null})
   * @param record the event to fold in
   * @return next state (must not be {@code null})
   */
  S apply(S state, LogRecord record);
}
