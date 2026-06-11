/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.risk;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;

/**
 * Versioned risk-appetite parameters (task 10.6, arch-risk-engine § register/amend): notional caps
 * per scope (firm / desk / account). A {@code hard} cap blocks; a {@code soft} cap warns. Every
 * amendment requires a change reason and sign-off identity and lands in the journal — risk
 * parameters are governed reference data, not code.
 */
public final class RiskLimits {

  public enum Scope {
    FIRM,
    DESK,
    ACCOUNT
  }

  /** Notional caps for one scope owner; null = no cap of that kind. */
  public record Limits(
      @Nullable Long maxGrossNotionalHard,
      @Nullable Long maxGrossNotionalSoft,
      @Nullable Long maxNetNotionalHard,
      @Nullable Long maxNetNotionalSoft) {}

  /** One journaled parameter amendment. */
  public record Amendment(
      long version,
      Scope scope,
      String owner,
      Limits limits,
      String changeReason,
      String signedOffBy) {}

  private final ConcurrentHashMap<String, Limits> limits = new ConcurrentHashMap<>();
  private final List<Amendment> journal = java.util.Collections.synchronizedList(new ArrayList<>());
  private final AtomicLong version = new AtomicLong(0);

  /** Register or amend a scope's limits. Returns the new parameter version. */
  public long set(
      Scope scope, String owner, Limits value, String changeReason, String signedOffBy) {
    Objects.requireNonNull(value, "limits");
    Objects.requireNonNull(changeReason, "changeReason");
    Objects.requireNonNull(signedOffBy, "signedOffBy");
    limits.put(key(scope, owner), value);
    long v = version.incrementAndGet();
    journal.add(new Amendment(v, scope, owner, value, changeReason, signedOffBy));
    return v;
  }

  public Optional<Limits> get(Scope scope, String owner) {
    return Optional.ofNullable(limits.get(key(scope, owner)));
  }

  /** Current parameter version (recorded on every risk decision for replay). */
  public long version() {
    return version.get();
  }

  public List<Amendment> journal() {
    synchronized (journal) {
      return List.copyOf(journal);
    }
  }

  private static String key(Scope scope, String owner) {
    return scope + "|" + owner;
  }
}
