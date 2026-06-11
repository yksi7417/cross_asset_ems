/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.compliance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;

/**
 * Allow / restricted / watch lists (task 10.4, arch-compliance § lists): versioned reference data
 * managed by compliance, never by engineering releases. Firm-level RESTRICTED entries hard-block
 * (corporate-action conflicts, MNPI); a desk in allow-list mode only trades its positive ALLOW
 * list; firm WATCH entries warn for heightened monitoring. Entries carry effective-from and
 * optional expiry timestamps; every mutation bumps the version and lands in the change journal (the
 * event-log feed of "lists are versioned reference data; every change is an event").
 */
public final class ComplianceListService {

  /** Which list an entry lives on; RESTRICTED/WATCH are firm-scoped, ALLOW is desk-scoped. */
  public enum Kind {
    RESTRICTED,
    ALLOW,
    WATCH
  }

  /** One list entry; active in {@code [effectiveFromMillis, expiresAtMillis)}. */
  public record Entry(String figi, long effectiveFromMillis, @Nullable Long expiresAtMillis) {
    public boolean activeAt(long nowMillis) {
      return nowMillis >= effectiveFromMillis
          && (expiresAtMillis == null || nowMillis < expiresAtMillis);
    }
  }

  /** One journaled mutation. */
  public record ListChange(long version, String action, Kind kind, String owner, String figi) {}

  private final ConcurrentHashMap<String, Map<String, Entry>> lists = new ConcurrentHashMap<>();
  private final Set<String> allowListDesks = ConcurrentHashMap.newKeySet();
  private final List<ListChange> journal =
      java.util.Collections.synchronizedList(new ArrayList<>());
  private final AtomicLong version = new AtomicLong(0);

  /** Add (or replace) an entry. Returns the new list version. */
  public long add(
      Kind kind,
      String owner,
      String figi,
      long effectiveFromMillis,
      @Nullable Long expiresAtMillis) {
    Objects.requireNonNull(kind, "kind");
    lists
        .computeIfAbsent(key(kind, owner), k -> new ConcurrentHashMap<>())
        .put(figi, new Entry(figi, effectiveFromMillis, expiresAtMillis));
    long v = version.incrementAndGet();
    journal.add(new ListChange(v, "ADD", kind, owner, figi));
    return v;
  }

  /** Remove an entry. Returns the new list version (unchanged if the entry was absent). */
  public long remove(Kind kind, String owner, String figi) {
    Map<String, Entry> list = lists.get(key(kind, owner));
    if (list == null || list.remove(figi) == null) {
      return version.get();
    }
    long v = version.incrementAndGet();
    journal.add(new ListChange(v, "REMOVE", kind, owner, figi));
    return v;
  }

  /** Switch a desk into (or out of) positive allow-list mode. */
  public long setAllowListMode(String desk, boolean enabled) {
    boolean changed = enabled ? allowListDesks.add(desk) : allowListDesks.remove(desk);
    if (!changed) {
      return version.get();
    }
    long v = version.incrementAndGet();
    journal.add(
        new ListChange(v, enabled ? "ALLOW_MODE_ON" : "ALLOW_MODE_OFF", Kind.ALLOW, desk, ""));
    return v;
  }

  /** True if the desk only trades instruments on its ALLOW list. */
  public boolean usesAllowList(String desk) {
    return allowListDesks.contains(desk);
  }

  /** True if {@code figi} has an entry on the given list that is active at {@code nowMillis}. */
  public boolean isActive(Kind kind, String owner, String figi, long nowMillis) {
    Map<String, Entry> list = lists.get(key(kind, owner));
    if (list == null) {
      return false;
    }
    Entry entry = list.get(figi);
    return entry != null && entry.activeAt(nowMillis);
  }

  /** Current list version (monotonic across all lists). */
  public long version() {
    return version.get();
  }

  /** Immutable snapshot of the change journal. */
  public List<ListChange> journal() {
    synchronized (journal) {
      return List.copyOf(journal);
    }
  }

  private static String key(Kind kind, String owner) {
    return kind + "|" + owner;
  }
}
