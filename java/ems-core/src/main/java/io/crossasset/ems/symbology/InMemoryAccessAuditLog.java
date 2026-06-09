/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.symbology;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/** In-memory audit log. Thread-safe via copy-on-write. */
public class InMemoryAccessAuditLog implements AccessAuditLog {

  private final List<AccessRecord> log = new CopyOnWriteArrayList<>();

  @Override
  public void record(AccessRecord entry) {
    log.add(entry);
  }

  @Override
  public List<AccessRecord> entries(String identity) {
    return log.stream()
        .filter(r -> identity.equals(r.identity()))
        .collect(Collectors.toUnmodifiableList());
  }
}
