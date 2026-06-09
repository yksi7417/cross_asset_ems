/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.core.config;

/**
 * Read interface for the configuration subsystem.
 *
 * <p>Components obtain the current {@link ConfigSnapshot} and read from it directly. The snapshot
 * reference is replaced atomically at message boundaries (task 3.8 — local cache snapshot agent).
 *
 * <p>Task 3.7 — Configuration service.
 */
public interface ConfigService {

  /** Returns the current configuration snapshot. Never {@code null}. */
  ConfigSnapshot currentSnapshot();

  /**
   * Publishes a new snapshot, making it the result of subsequent {@link #currentSnapshot()} calls.
   *
   * <p>In production, this is called by the local cache agent (task 3.8) at message boundaries. In
   * tests, it is called directly to inject fixture snapshots.
   */
  void publish(ConfigSnapshot snapshot);
}
