/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.core.config;

import io.crossasset.ems.core.clock.Timestamp;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Local cache snapshot agent — the only config entry point on the hot path.
 *
 * <p>Runs as a sidecar on each host. Receives {@link ConfigSnapshot} objects from the config
 * multicast bus via {@link #stageSnapshot(ConfigSnapshot)}, then promotes them to the live {@link
 * #currentSnapshot()} atomically at each message boundary.
 *
 * <h3>Core invariant — one message, one config view</h3>
 *
 * All decisions inside a single message handler see the same snapshot. A snapshot that arrives
 * during message processing is staged; it does not become current until the FSM dispatcher calls
 * {@link #onMessageBoundary()} between {@code handleMessage()} invocations.
 *
 * <h3>Threading model</h3>
 *
 * <ul>
 *   <li>{@link #stageSnapshot} — called from the Aeron bus subscriber thread.
 *   <li>{@link #onMessageBoundary} and {@link #currentSnapshot} — called from the single
 *       message-processing thread.
 *   <li>{@link #publish} — immediate; may be called from any thread.
 * </ul>
 *
 * <p>Task 3.8 — local cache snapshot agent, atomic message-boundary swap.
 */
public final class LocalCacheAgent implements ConfigService {

  /**
   * The snapshot currently visible to components via {@link #currentSnapshot()}. Written by {@link
   * #publish} (which is called by the message thread at boundaries, and potentially by other
   * callers). Volatile ensures cross-thread visibility.
   */
  private volatile ConfigSnapshot current;

  /**
   * Snapshot staged by the bus subscriber for promotion at the next boundary. {@code null} when no
   * new snapshot has arrived since the last boundary. AtomicReference allows the bus thread and
   * message thread to coordinate without locks.
   */
  private final AtomicReference<ConfigSnapshot> staged = new AtomicReference<>(null);

  /**
   * Constructs the agent with an empty initial snapshot at the given wall-clock time.
   *
   * @param initialTime the timestamp for the version-0 bootstrap snapshot
   */
  public LocalCacheAgent(Timestamp initialTime) {
    this.current = ConfigSnapshot.builder(0L, initialTime).build();
  }

  /**
   * Stages an incoming snapshot for promotion at the next message boundary.
   *
   * <p>If multiple snapshots arrive before the next boundary, only the latest is promoted
   * (last-write-wins — the config bus delivers a total order of version-stamped snapshots).
   *
   * <p>May be called from any thread (typically the Aeron bus subscriber thread).
   *
   * @param incoming the new snapshot received from the config multicast bus
   */
  public void stageSnapshot(ConfigSnapshot incoming) {
    staged.set(incoming);
  }

  /**
   * Called by the FSM dispatcher at each message boundary — between {@code handleMessage()}
   * invocations, never inside one.
   *
   * <p>Atomically promotes the staged snapshot (if any) by calling {@link #publish}. After this
   * returns, all components on this thread see the promoted snapshot for the next message.
   *
   * <p>A no-op if no snapshot has been staged since the last boundary.
   */
  public void onMessageBoundary() {
    ConfigSnapshot next = staged.getAndSet(null);
    if (next != null) {
      publish(next);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Immediately replaces the current snapshot. After this returns, {@link #currentSnapshot()}
   * reflects the new value. In production the agent calls this at message boundaries via {@link
   * #onMessageBoundary}; in tests it can be called directly to inject snapshots.
   */
  @Override
  public void publish(ConfigSnapshot snapshot) {
    current = snapshot;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the snapshot that was current at the start of the current message. Never {@code
   * null}.
   */
  @Override
  public ConfigSnapshot currentSnapshot() {
    return current;
  }
}
