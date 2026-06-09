/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.refdata;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable point-in-time snapshot of all records in a single reference-data domain.
 *
 * <p>Same immutable-value discipline as {@code ConfigSnapshot} and {@code SecurityMasterSnapshot}.
 * Consumers hold an {@code AtomicReference} to this snapshot and read from it on the hot path
 * without lock contention. The local cache agent swaps the reference at event boundaries.
 *
 * <p>{@link #apply(RefDataEvent)} returns a new snapshot without mutating the current one.
 *
 * <p>Task 4.21 — Reference data service.
 */
public final class RefDataSnapshot<V> {

  /** An empty snapshot with no records. */
  @SuppressWarnings("rawtypes")
  private static final RefDataSnapshot EMPTY = new RefDataSnapshot<>(Map.of());

  @SuppressWarnings("unchecked")
  public static <V> RefDataSnapshot<V> empty() {
    return EMPTY;
  }

  private final Map<String, RefDataRecord<V>> store;

  private RefDataSnapshot(Map<String, RefDataRecord<V>> store) {
    this.store = store;
  }

  public Optional<RefDataRecord<V>> lookup(String key) {
    return Optional.ofNullable(store.get(key));
  }

  public int size() {
    return store.size();
  }

  public Map<String, RefDataRecord<V>> all() {
    return store;
  }

  /**
   * Applies {@code event} and returns a new snapshot. The current snapshot is not mutated.
   *
   * @throws IllegalStateException for a Retired event that references an unknown key
   */
  public RefDataSnapshot<V> apply(RefDataEvent<V> event) {
    return switch (event) {
      case RefDataEvent.Added<V> e -> applyAdded(e);
      case RefDataEvent.Amended<V> e -> applyAmended(e);
      case RefDataEvent.Retired<V> e -> applyRetired(e);
    };
  }

  private RefDataSnapshot<V> applyAdded(RefDataEvent.Added<V> e) {
    Map<String, RefDataRecord<V>> next = mutableCopy();
    next.put(e.key(), e.record());
    return frozen(next);
  }

  private RefDataSnapshot<V> applyAmended(RefDataEvent.Amended<V> e) {
    Map<String, RefDataRecord<V>> next = mutableCopy();
    next.put(e.key(), e.record());
    return frozen(next);
  }

  private RefDataSnapshot<V> applyRetired(RefDataEvent.Retired<V> e) {
    if (!store.containsKey(e.key())) {
      throw new IllegalStateException(
          "RefDataEvent.Retired references unknown key: " + e.domain() + "/" + e.key());
    }
    Map<String, RefDataRecord<V>> next = mutableCopy();
    RefDataRecord<V> prior = next.get(e.key());
    next.put(
        e.key(), prior.retire(e.occurredAt(), "system", e.occurredAt(), "explicit retirement"));
    return frozen(next);
  }

  private Map<String, RefDataRecord<V>> mutableCopy() {
    return new HashMap<>(store);
  }

  private static <V> RefDataSnapshot<V> frozen(Map<String, RefDataRecord<V>> m) {
    return new RefDataSnapshot<>(Collections.unmodifiableMap(m));
  }
}
