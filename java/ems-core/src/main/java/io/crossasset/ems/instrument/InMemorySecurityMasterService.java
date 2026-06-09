/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe in-memory implementation of {@link SecurityMasterService}.
 *
 * <p>Uses an {@link AtomicReference} so that the hot-path reader threads see a consistent snapshot
 * while the cache-agent writer atomically installs the next version. Reads are lock-free; writes
 * are a single CAS.
 *
 * <p>Task 4.19 — Security master CRUD + supersession events.
 */
public final class InMemorySecurityMasterService implements SecurityMasterService {

  private final AtomicReference<SecurityMasterSnapshot> ref =
      new AtomicReference<>(SecurityMasterSnapshot.EMPTY);

  @Override
  public SecurityMasterSnapshot currentSnapshot() {
    return ref.get();
  }

  @Override
  public void publish(SecurityMasterSnapshot snapshot) {
    ref.set(snapshot);
  }
}
