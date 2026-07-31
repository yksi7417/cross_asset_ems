/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.core.journal;

import java.util.Collections;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * One line of an event journal.
 *
 * <p>{@code fields} is a {@link SortedMap} rather than a {@code Map} on purpose: its iteration
 * order reaches the output journal, and the conformance gate compares that journal byte-for-byte
 * across three languages. An unordered map here would make the gate fail intermittently on whatever
 * the JVM's hash order happened to be, which is the worst kind of failure to debug.
 *
 * @param seq monotonically increasing sequence number, starting at 1
 * @param type event type name, e.g. {@code OrderNew}
 * @param fields event payload; keys iterate in lexicographic order
 */
public record JournalEvent(long seq, String type, SortedMap<String, String> fields) {

  public JournalEvent {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(fields, "fields");
    if (seq < 0) {
      throw new IllegalArgumentException("seq must not be negative: " + seq);
    }
    // Defensive copy into a TreeMap: callers may hand over a HashMap, and an
    // unmodifiable view of a HashMap would still iterate in hash order.
    fields = Collections.unmodifiableSortedMap(new TreeMap<>(fields));
  }

  /** Convenience factory for an event with no payload. */
  public static JournalEvent of(long seq, String type) {
    return new JournalEvent(seq, type, new TreeMap<>());
  }

  /** Returns a copy of this event carrying a different sequence number. */
  public JournalEvent withSeq(long newSeq) {
    return new JournalEvent(newSeq, type, fields);
  }
}
