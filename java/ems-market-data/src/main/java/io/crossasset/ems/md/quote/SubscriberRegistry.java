/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.md.quote;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Subscriber-visibility registry (task 9.2, [[arch-quote-server]] § Transport): records WHO is on
 * each topic even when delivery is multicast (a multicast tail has no per-subscriber socket, so
 * without this the question "who sees this quote?" is unanswerable — and compliance asks it).
 * Subscribers heartbeat; a missing heartbeat past the TTL prunes the entry. Deterministic:
 * registration order is preserved, pruning is clock-driven via arguments.
 */
public final class SubscriberRegistry {

  /** One live subscription entry. */
  public record Entry(
      String subscriberId,
      String topicPattern,
      QuoteServer.Qos qos,
      long throttlePerSec,
      long registeredAtMillis,
      long lastHeartbeatMillis) {}

  private final Map<String, Entry> byId = new LinkedHashMap<>();
  private int seq = 0;

  /** Register a subscription; returns its id. */
  public synchronized String register(
      String subscriberId,
      String topicPattern,
      QuoteServer.Qos qos,
      long throttlePerSec,
      long nowMillis) {
    String id = "SUB-" + ++seq;
    byId.put(
        id,
        new Entry(
            Objects.requireNonNull(subscriberId),
            Objects.requireNonNull(topicPattern),
            qos,
            throttlePerSec,
            nowMillis,
            nowMillis));
    return id;
  }

  public synchronized void unregister(String subscriptionId) {
    byId.remove(subscriptionId);
  }

  /**
   * Record a heartbeat; unknown ids are ignored (already pruned — the subscriber re-subscribes).
   */
  public synchronized void heartbeat(String subscriptionId, long nowMillis) {
    Entry entry = byId.get(subscriptionId);
    if (entry != null) {
      byId.put(
          subscriptionId,
          new Entry(
              entry.subscriberId(),
              entry.topicPattern(),
              entry.qos(),
              entry.throttlePerSec(),
              entry.registeredAtMillis(),
              nowMillis));
    }
  }

  /** Prune every entry whose last heartbeat is older than {@code ttlMillis}; returns pruned ids. */
  public synchronized List<String> prune(long nowMillis, long ttlMillis) {
    List<String> pruned = new ArrayList<>();
    byId.entrySet()
        .removeIf(
            e -> {
              boolean stale = nowMillis - e.getValue().lastHeartbeatMillis() > ttlMillis;
              if (stale) {
                pruned.add(e.getKey());
              }
              return stale;
            });
    return pruned;
  }

  /** Who is on {@code topic} — the compliance answer, deterministic order. */
  public synchronized List<Entry> whoIsOn(String topic) {
    return new ArrayList<>(whoIsOnById(topic).values());
  }

  /** Same, keyed by subscription id (the delivery path needs the id for throttling). */
  public synchronized Map<String, Entry> whoIsOnById(String topic) {
    Map<String, Entry> out = new LinkedHashMap<>();
    for (Map.Entry<String, Entry> e : byId.entrySet()) {
      if (matches(e.getValue().topicPattern(), topic)) {
        out.put(e.getKey(), e.getValue());
      }
    }
    return out;
  }

  /** All entries for {@code subscriptionId}. */
  public synchronized Map<String, Entry> all() {
    return new LinkedHashMap<>(byId);
  }

  /** Glob match: {@code quote.BBG000B9XRY4.*} covers l1/l2/trade; exact otherwise. */
  static boolean matches(String pattern, String topic) {
    if (pattern.endsWith(".*")) {
      String prefix = pattern.substring(0, pattern.length() - 1); // keep the dot
      return topic.startsWith(prefix);
    }
    return pattern.equals(topic);
  }
}
