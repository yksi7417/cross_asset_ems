/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.transport.projection;

/**
 * Identifies a bounded slice of the event log by inclusive {@code globalSeq} range.
 *
 * <p>The slice is used by {@link ReplayEngine} to limit which records are presented to projections.
 * Both {@link #fromGlobalSeq()} and {@link #toGlobalSeq()} are inclusive; a record is in the slice
 * iff {@code fromGlobalSeq <= record.globalSeq() <= toGlobalSeq}.
 *
 * <p>Task 3.5 — Replay engine.
 */
public record ReplaySlice(long fromGlobalSeq, long toGlobalSeq) {

  public ReplaySlice {
    if (fromGlobalSeq < 0) {
      throw new IllegalArgumentException("fromGlobalSeq must be >= 0, got " + fromGlobalSeq);
    }
    if (toGlobalSeq < fromGlobalSeq) {
      throw new IllegalArgumentException(
          "toGlobalSeq must be >= fromGlobalSeq, got to=" + toGlobalSeq + " from=" + fromGlobalSeq);
    }
  }

  /** Covers the entire log from seq 0 to {@code Long.MAX_VALUE}. */
  public static ReplaySlice all() {
    return new ReplaySlice(0, Long.MAX_VALUE);
  }

  /** Covers all records from {@code from} onwards. */
  public static ReplaySlice from(long from) {
    return new ReplaySlice(from, Long.MAX_VALUE);
  }

  /** Covers records from {@code from} to {@code to}, both inclusive. */
  public static ReplaySlice between(long from, long to) {
    return new ReplaySlice(from, to);
  }

  /** Returns {@code true} if {@code globalSeq} falls within this slice (both ends inclusive). */
  public boolean contains(long globalSeq) {
    return globalSeq >= fromGlobalSeq && globalSeq <= toGlobalSeq;
  }
}
