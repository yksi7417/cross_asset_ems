/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe allocator for {@link OtcInternalId} values within a firm + instrument-class
 * namespace.
 *
 * <p>Each allocator maintains an atomic counter starting at 1. The constructor validates that the
 * static prefix ({@code ems_iid:{firmId}:{instrumentClass}:}) leaves room for at least one counter
 * digit within the 20-character limit, failing fast rather than at allocation time.
 *
 * <p>Allocator state is in-memory only. Callers are responsible for persisting the last-issued
 * counter to survive restarts (e.g., by event-sourcing the allocation events or by reading the
 * highest counter from the security master at startup).
 *
 * <p>Task 4.25 — Internal-allocated identifier namespace for OTC.
 */
public final class OtcInternalIdAllocator {

  private final String firmId;
  private final String instrumentClass;
  private final AtomicLong counter;

  /**
   * Creates an allocator starting at counter 1.
   *
   * @throws IllegalArgumentException if the static parts of the ID already exceed the 20-char limit
   */
  public OtcInternalIdAllocator(String firmId, String instrumentClass) {
    this(firmId, instrumentClass, 0L);
  }

  /**
   * Creates an allocator resuming from {@code lastIssuedCounter} (so the first {@link #allocate()}
   * returns {@code lastIssuedCounter + 1}).
   *
   * @throws IllegalArgumentException if the static parts of the ID already exceed the 20-char limit
   */
  public OtcInternalIdAllocator(String firmId, String instrumentClass, long lastIssuedCounter) {
    // Validate with counter=1 to check that at least the first ID fits
    new OtcInternalId(firmId, instrumentClass, 1L);
    this.firmId = firmId;
    this.instrumentClass = instrumentClass;
    this.counter = new AtomicLong(lastIssuedCounter);
  }

  /**
   * Allocates and returns the next {@link OtcInternalId}.
   *
   * @throws IllegalStateException if the next counter would produce an ID longer than 20 characters
   */
  public OtcInternalId allocate() {
    long seq = counter.incrementAndGet();
    try {
      return new OtcInternalId(firmId, instrumentClass, seq);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          "Counter overflow: OTC internal ID would exceed 20 chars at counter " + seq, e);
    }
  }

  /** Returns the last counter value issued, or 0 if no allocation has occurred yet. */
  public long lastIssuedCounter() {
    return counter.get();
  }

  public String firmId() {
    return firmId;
  }

  public String instrumentClass() {
    return instrumentClass;
  }
}
