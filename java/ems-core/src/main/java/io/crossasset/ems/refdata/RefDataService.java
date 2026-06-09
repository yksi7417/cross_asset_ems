/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.refdata;

/**
 * Read interface for a single reference-data domain.
 *
 * <p>Components hold an instance of this service, call {@link #currentSnapshot()} to get the
 * current point-in-time view, and read from the snapshot on the hot path. The local cache agent
 * calls {@link #publish(RefDataSnapshot)} at event boundaries.
 *
 * <p>Task 4.21 — Reference data service.
 */
public interface RefDataService<V> {

  /** Returns the current snapshot. Never {@code null}. */
  RefDataSnapshot<V> currentSnapshot();

  /**
   * Publishes a new snapshot, making it the result of subsequent {@link #currentSnapshot()} calls.
   */
  void publish(RefDataSnapshot<V> snapshot);
}
