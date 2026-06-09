/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.core.config;

import io.crossasset.ems.core.clock.Timestamp;

/**
 * In-memory {@link ConfigService} for tests and replay.
 *
 * <p>Holds a single {@link ConfigSnapshot} reference. {@link #publish(ConfigSnapshot)} replaces it
 * atomically (volatile write). In tests, snapshots are built with {@link ConfigSnapshot#builder}
 * and injected via {@code publish}.
 *
 * <p>Task 3.7 — Configuration service.
 */
public class InMemoryConfigService implements ConfigService {

  private volatile ConfigSnapshot snapshot;

  public InMemoryConfigService(Timestamp initialTime) {
    this.snapshot = ConfigSnapshot.builder(0L, initialTime).build();
  }

  @Override
  public ConfigSnapshot currentSnapshot() {
    return snapshot;
  }

  @Override
  public void publish(ConfigSnapshot snapshot) {
    this.snapshot = snapshot;
  }
}
