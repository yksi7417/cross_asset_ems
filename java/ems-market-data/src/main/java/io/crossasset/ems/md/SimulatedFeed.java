/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.md;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic in-memory {@link MarketDataFeed} (task 18.12): the CI stand-in for the Bloomberg
 * adapter (18.13 runs against a desk terminal at runtime, this in tests), the desktop dev-mode
 * feed, and the reference implementation of the SPI contract. Tests drive it explicitly — {@link
 * #emit}, {@link #failSubscription}, {@link #setHealth} — so tick order and timestamps are fully
 * under the caller's control (replay-friendly: no internal clock, no randomness).
 */
public final class SimulatedFeed implements MarketDataFeed {

  private record Sub(String id, String figi, Set<MdField> fields, Listener listener) {}

  private final String feedId;
  // LinkedHashMap under the monitor: deterministic delivery order = subscription order.
  private final Map<String, Sub> subs = new LinkedHashMap<>();
  private final List<HealthListener> healthListeners = new ArrayList<>();
  private final AtomicLong subIdSeq = new AtomicLong(1);
  private FeedHealth health = FeedHealth.of(FeedHealth.Status.DOWN, "not started", 0);

  public SimulatedFeed(String feedId) {
    this.feedId = Objects.requireNonNull(feedId, "feedId");
  }

  @Override
  public String feedId() {
    return feedId;
  }

  @Override
  public synchronized void start() {
    if (health.status() != FeedHealth.Status.UP) {
      setHealth(FeedHealth.Status.UP, "connected", health.atMillis());
    }
  }

  @Override
  public synchronized void close() {
    if (health.status() != FeedHealth.Status.DOWN) {
      setHealth(FeedHealth.Status.DOWN, "closed", health.atMillis());
    }
    subs.clear();
  }

  @Override
  public synchronized FeedHealth health() {
    return health;
  }

  @Override
  public synchronized void addHealthListener(HealthListener listener) {
    healthListeners.add(Objects.requireNonNull(listener, "listener"));
    listener.onHealth(feedId, health);
  }

  @Override
  public synchronized String subscribe(String figi, Set<MdField> fields, Listener listener) {
    Objects.requireNonNull(figi, "figi");
    Objects.requireNonNull(listener, "listener");
    String id = "MD-" + feedId + "-" + subIdSeq.getAndIncrement();
    subs.put(id, new Sub(id, figi, Set.copyOf(Objects.requireNonNull(fields, "fields")), listener));
    return id;
  }

  @Override
  public synchronized boolean unsubscribe(String subscriptionId) {
    return subs.remove(subscriptionId) != null;
  }

  // ── Test/dev drivers ─────────────────────────────────────────────────────────

  /**
   * Emit one update for {@code figi}: every subscriber of that FIGI receives the intersection of
   * {@code values} with its requested field set; an empty intersection delivers nothing (the SPI's
   * filtered-tick rule).
   */
  public synchronized void emit(String figi, Map<MdField, Long> values, long atMillis) {
    for (Sub sub : subs.values()) {
      if (!sub.figi().equals(figi)) {
        continue;
      }
      Map<MdField, Long> filtered = new LinkedHashMap<>();
      for (Map.Entry<MdField, Long> entry : values.entrySet()) {
        if (sub.fields().contains(entry.getKey())) {
          filtered.put(entry.getKey(), entry.getValue());
        }
      }
      if (!filtered.isEmpty()) {
        sub.listener().onTick(new MdTick(feedId, figi, filtered, atMillis));
      }
    }
  }

  /** Simulate a provider-side per-subscription failure (entitlement denial, unknown symbol). */
  public synchronized void failSubscription(String figi, String code, String message) {
    for (Sub sub : subs.values()) {
      if (sub.figi().equals(figi)) {
        sub.listener().onSubscriptionError(figi, code, message);
      }
    }
  }

  /** Drive a health transition; all health listeners are notified. */
  public synchronized void setHealth(FeedHealth.Status status, String detail, long atMillis) {
    health = FeedHealth.of(status, detail, atMillis);
    for (HealthListener listener : healthListeners) {
      listener.onHealth(feedId, health);
    }
  }
}
