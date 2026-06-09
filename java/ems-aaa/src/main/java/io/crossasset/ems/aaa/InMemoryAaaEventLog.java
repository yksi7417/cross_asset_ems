/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thread-safe in-memory event log for testing and skeleton use.
 *
 * <p>Task 5.1 — AAA service skeleton.
 */
public final class InMemoryAaaEventLog implements AaaEventLog {

  private final List<AaaEvent> events = Collections.synchronizedList(new ArrayList<>());

  @Override
  public void record(AaaEvent event) {
    events.add(event);
  }

  /** Returns a snapshot of all recorded events. */
  public List<AaaEvent> events() {
    synchronized (events) {
      return Collections.unmodifiableList(new ArrayList<>(events));
    }
  }
}
