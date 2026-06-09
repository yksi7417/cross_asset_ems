/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.instrument;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable point-in-time snapshot of all active instruments in the security master.
 *
 * <p>The hot-path access pattern: consumers load an atomic reference to this snapshot and call
 * {@link #lookup(String)} or {@link #lookup(String, long)} without network calls or lock
 * contention. The local cache agent atomically swaps the snapshot reference at message boundaries.
 *
 * <p>{@link #apply(SecurityMasterEvent)} returns a new snapshot reflecting the event, following the
 * same immutable-value discipline as {@code ConfigSnapshot}.
 *
 * <p>Task 4.19 — Security master CRUD + supersession events.
 */
public final class SecurityMasterSnapshot {

  public static final SecurityMasterSnapshot EMPTY = new SecurityMasterSnapshot(Map.of());

  /** {@code figi → (versionSeq → InstrumentVersioned)} */
  private final Map<String, Map<Long, InstrumentVersioned>> store;

  private SecurityMasterSnapshot(Map<String, Map<Long, InstrumentVersioned>> store) {
    this.store = store;
  }

  /**
   * Returns the latest version of the instrument with {@code figi}, or empty if not present.
   *
   * <p>Latest is defined as the entry with the highest {@code versionSeq}.
   */
  public Optional<InstrumentVersioned> lookup(String figi) {
    Map<Long, InstrumentVersioned> versions = store.get(figi);
    if (versions == null || versions.isEmpty()) return Optional.empty();
    return versions.values().stream()
        .max((a, b) -> Long.compareUnsigned(a.versionSeq(), b.versionSeq()));
  }

  /**
   * Returns the exact version of the instrument with {@code figi} and {@code versionSeq}, or empty
   * if not present. Used for replay: the order message carries {@code (figi, instrumentVersion)}.
   */
  public Optional<InstrumentVersioned> lookup(String figi, long versionSeq) {
    Map<Long, InstrumentVersioned> versions = store.get(figi);
    if (versions == null) return Optional.empty();
    return Optional.ofNullable(versions.get(versionSeq));
  }

  /** Returns the number of distinct FIGIs tracked in this snapshot. */
  public int figiCount() {
    return store.size();
  }

  /**
   * Applies {@code event} and returns a new snapshot. The current snapshot is not mutated.
   *
   * @throws IllegalStateException if the event references an unknown FIGI (for Superseded/Retired)
   */
  public SecurityMasterSnapshot apply(SecurityMasterEvent event) {
    return switch (event) {
      case SecurityMasterEvent.InstrumentCreated e -> applyCreated(e);
      case SecurityMasterEvent.InstrumentSuperseded e -> applySuperseded(e);
      case SecurityMasterEvent.InstrumentRetired e -> applyRetired(e);
    };
  }

  private SecurityMasterSnapshot applyCreated(SecurityMasterEvent.InstrumentCreated e) {
    Map<String, Map<Long, InstrumentVersioned>> next = mutableCopy();
    next.computeIfAbsent(e.instrument().figi(), k -> new HashMap<>())
        .put(e.instrument().versionSeq(), e.instrument());
    return frozen(next);
  }

  private SecurityMasterSnapshot applySuperseded(SecurityMasterEvent.InstrumentSuperseded e) {
    Map<String, Map<Long, InstrumentVersioned>> next = mutableCopy();
    next.computeIfAbsent(e.figi(), k -> new HashMap<>())
        .put(e.newVersion().versionSeq(), e.newVersion());
    return frozen(next);
  }

  private SecurityMasterSnapshot applyRetired(SecurityMasterEvent.InstrumentRetired e) {
    Map<Long, InstrumentVersioned> versions = store.get(e.figi());
    if (versions == null || !versions.containsKey(e.versionSeq())) {
      throw new IllegalStateException(
          "InstrumentRetired references unknown FIGI/version: " + e.figi() + " v" + e.versionSeq());
    }
    Map<String, Map<Long, InstrumentVersioned>> next = mutableCopy();
    InstrumentVersioned prior = next.get(e.figi()).get(e.versionSeq());
    InstrumentCore retiredCore =
        new InstrumentCore(
            prior.core().figi(),
            prior.core().internalIid(),
            prior.core().compositeFigi(),
            prior.core().shareClassFigi(),
            prior.core().assetClass(),
            prior.core().instrumentType(),
            prior.core().displayName(),
            prior.core().legalName(),
            prior.core().issuerLei(),
            prior.core().currency(),
            prior.core().countryOfIssue(),
            prior.core().countryOfListing(),
            prior.core().fungibility(),
            prior.core().settlementConvention(),
            prior.core().tickSizeRegimeRef(),
            e.terminalStatus(),
            prior.core().effectiveFrom(),
            prior.core().effectiveFrom(),
            prior.core().versionSeq(),
            prior.core().supersededBy(),
            prior.core().createdAt(),
            e.occurredAt());
    next.get(e.figi()).put(e.versionSeq(), new InstrumentVersioned(retiredCore, null));
    return frozen(next);
  }

  private Map<String, Map<Long, InstrumentVersioned>> mutableCopy() {
    Map<String, Map<Long, InstrumentVersioned>> copy = new HashMap<>();
    for (var entry : store.entrySet()) {
      copy.put(entry.getKey(), new HashMap<>(entry.getValue()));
    }
    return copy;
  }

  private static SecurityMasterSnapshot frozen(Map<String, Map<Long, InstrumentVersioned>> m) {
    Map<String, Map<Long, InstrumentVersioned>> frozen = new HashMap<>();
    for (var e : m.entrySet()) {
      frozen.put(e.getKey(), Collections.unmodifiableMap(e.getValue()));
    }
    return new SecurityMasterSnapshot(Collections.unmodifiableMap(frozen));
  }
}
