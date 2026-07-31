/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.transport.journal;

import io.crossasset.ems.core.journal.JournalEvent;
import java.util.List;

/**
 * How the slice receives and emits events, without knowing what carries them.
 *
 * <p>Two implementations are intended: {@link JournalTransport}, which reads and writes files and
 * is what the conformance gate runs, and an Aeron-backed one for the live path. The slice is
 * written against this interface so the gate can be deterministic while production is not.
 *
 * <p>Single-threaded by design — see {@code docs/decisions/0003-shared-schemas-corpus-harness.md}.
 * A concurrent transport is a later, separately-gated concern.
 */
public interface Transport extends AutoCloseable {

  /**
   * Returns everything available to consume, and consumes it.
   *
   * <p>A second call returns an empty list rather than replaying: draining twice is a programming
   * error the interface makes visible instead of quietly duplicating an order.
   */
  List<JournalEvent> drain();

  /**
   * Queues an event for emission.
   *
   * <p>Nothing is visible to a reader until {@link #flush()}. A half-written journal from a crashed
   * run would look exactly like a legitimately short one, and the conformance gate cannot tell the
   * difference.
   */
  void publish(JournalEvent event);

  /** Makes every published event visible, as one unit. */
  void flush();

  /** Flushes, then releases resources. Idempotent. */
  @Override
  void close();
}
