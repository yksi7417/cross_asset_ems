/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.refdata;

import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory implementation of {@link RefDataService} using an {@link AtomicReference} for lock-free
 * hot-path reads.
 *
 * <p>Task 4.21 — Reference data service.
 */
public final class InMemoryRefDataService<V> implements RefDataService<V> {

  private final AtomicReference<RefDataSnapshot<V>> snapshot;

  public InMemoryRefDataService() {
    this.snapshot = new AtomicReference<>(RefDataSnapshot.empty());
  }

  @Override
  public RefDataSnapshot<V> currentSnapshot() {
    return snapshot.get();
  }

  @Override
  public void publish(RefDataSnapshot<V> newSnapshot) {
    snapshot.set(newSnapshot);
  }
}
