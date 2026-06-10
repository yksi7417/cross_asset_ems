/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Topic-keyed pub/sub with cursor-resume semantics (task 8.4). Each topic carries a monotonic
 * sequence and a bounded replay buffer; a subscription names a topic and a starting sequence, so a
 * reconnecting client resumes from its last delivered seq with no missed and no doubled events —
 * the in-process realization of arch-api-first.md § Resume (the Aeron-backed stream binds in with
 * the transport edge).
 */
public final class SubscriptionRegistry {

  /** Replay buffer depth per topic (matches the session resend-window order of magnitude). */
  private static final int MAX_BUFFER = 10_000;

  private record Sub(String id, long sessionId, String topic, ApiEventSink sink) {}

  private static final class Topic {
    final AtomicLong seq = new AtomicLong(0);
    final ConcurrentSkipListMap<Long, ApiEvent> buffer = new ConcurrentSkipListMap<>();
  }

  private final ConcurrentHashMap<String, Topic> topics = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Sub> subs = new ConcurrentHashMap<>();
  private final AtomicLong subIdSeq = new AtomicLong(1);

  /**
   * Publish an event on a topic: assigns the next topic seq, buffers it for resume, and delivers to
   * every live subscriber of the topic.
   */
  public ApiEvent publish(String topic, String type, String refId, String payload) {
    Topic t = topics.computeIfAbsent(topic, k -> new Topic());
    ApiEvent event;
    synchronized (t) {
      long seq = t.seq.incrementAndGet();
      event = new ApiEvent(topic, seq, type, refId, payload);
      t.buffer.put(seq, event);
      while (t.buffer.size() > MAX_BUFFER) {
        t.buffer.pollFirstEntry();
      }
    }
    for (Sub sub : subs.values()) {
      if (sub.topic().equals(topic)) {
        sub.sink().deliver(sub.sessionId(), sub.id(), event);
      }
    }
    return event;
  }

  /**
   * Subscribe to a topic from {@code fromSeq} (inclusive): buffered events at or past the cursor
   * replay immediately in order, then live events follow. Returns the subscription ID.
   */
  public String subscribe(long sessionId, String topic, long fromSeq, ApiEventSink sink) {
    Objects.requireNonNull(topic, "topic");
    Objects.requireNonNull(sink, "sink");
    String id = "SUB-" + subIdSeq.getAndIncrement();
    Topic t = topics.computeIfAbsent(topic, k -> new Topic());
    List<ApiEvent> replay;
    synchronized (t) {
      replay = new ArrayList<>(t.buffer.tailMap(fromSeq, true).values());
      subs.put(id, new Sub(id, sessionId, topic, sink));
    }
    for (ApiEvent event : replay) {
      sink.deliver(sessionId, id, event);
    }
    return id;
  }

  /** Remove a subscription. Returns true if it existed. */
  public boolean unsubscribe(String subscriptionId) {
    return subs.remove(subscriptionId) != null;
  }

  /** Live subscriptions per session (diagnostics). */
  public Map<String, String> subscriptionsForSession(long sessionId) {
    Map<String, String> out = new ConcurrentHashMap<>();
    for (Sub sub : subs.values()) {
      if (sub.sessionId() == sessionId) {
        out.put(sub.id(), sub.topic());
      }
    }
    return out;
  }
}
