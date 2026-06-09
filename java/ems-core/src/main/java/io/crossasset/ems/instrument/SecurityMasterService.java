/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

/**
 * Read interface for the security master subsystem.
 *
 * <p>Follows the same snapshot-swap discipline as {@code ConfigService}: components obtain the
 * current {@link SecurityMasterSnapshot} and read from it directly. The local cache agent
 * atomically replaces the snapshot reference at message boundaries.
 *
 * <p>Task 4.19 — Security master CRUD + supersession events.
 */
public interface SecurityMasterService {

  /** Returns the current snapshot. Never {@code null}. */
  SecurityMasterSnapshot currentSnapshot();

  /**
   * Publishes a new snapshot, making it the result of subsequent {@link #currentSnapshot()} calls.
   *
   * <p>In production this is called by the local cache agent at message boundaries. In tests it is
   * called directly to inject fixture snapshots.
   */
  void publish(SecurityMasterSnapshot snapshot);
}
