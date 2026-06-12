/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.md.quote;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The internal quote server (task 9.1, [[arch-quote-server]]): one fabric distributing venue quotes
 * to every internal consumer — SOR market context (11.11), validators, automation, the desktop — on
 * the {@code quote.{figi}.{l1|l2|trade}} topic scheme with per-subscription QoS and throttling.
 * Publishers feed it; subscribers see exactly what their subscription says.
 *
 * <p>Deterministic: delivery happens inside {@link #publish} in registration order; throttling is
 * event-time-driven (the publish timestamp, never wall clock); snapshots return the last published
 * image per topic — the logon catch-up path.
 */
public final class QuoteServer {

  /** Delivery guarantee per subscription ([[arch-quote-server]] § Transport). */
  public enum Qos {
    /** Multicast tail: hot path, may gap under loss — the 9.3 multicast binding. */
    BEST_EFFORT,
    /** Unicast with replay catch-up after a gap. */
    GUARANTEED
  }

  /** One quote message on a topic. */
  public record QuoteUpdate(String topic, String figi, String payload, long atMillis) {}

  /** A subscriber's delivery callback. */
  @FunctionalInterface
  public interface QuoteListener {
    void onQuote(QuoteUpdate update);
  }

  private record Delivery(String subscriptionId, QuoteListener listener) {}

  private final SubscriberRegistry registry;
  private final Map<String, Delivery> deliveries = new LinkedHashMap<>();
  private final Map<String, QuoteUpdate> lastImage = new LinkedHashMap<>();
  private final Map<String, long[]> throttleState = new LinkedHashMap<>(); // [windowStart, count]

  public QuoteServer(SubscriberRegistry registry) {
    this.registry = Objects.requireNonNull(registry);
  }

  /**
   * Subscribe to a topic or glob ({@code quote.BBG000B9XRY4.*}). {@code throttlePerSec} of 0 =
   * unthrottled; otherwise at most that many messages per event-time second are delivered (the
   * downsampling knob for slow consumers — the desktop wants ticks, not every tick).
   */
  public synchronized String subscribe(
      String subscriberId,
      String topicPattern,
      Qos qos,
      long throttlePerSec,
      QuoteListener listener,
      long nowMillis) {
    String id = registry.register(subscriberId, topicPattern, qos, throttlePerSec, nowMillis);
    deliveries.put(id, new Delivery(id, listener));
    return id;
  }

  public synchronized void unsubscribe(String subscriptionId) {
    registry.unregister(subscriptionId);
    deliveries.remove(subscriptionId);
    throttleState.remove(subscriptionId);
  }

  /** Publish one quote; fan-out happens here, in subscription-registration order. */
  public synchronized void publish(QuoteUpdate update) {
    lastImage.put(update.topic(), update);
    for (Map.Entry<String, SubscriberRegistry.Entry> e :
        registry.whoIsOnById(update.topic()).entrySet()) {
      Delivery delivery = deliveries.get(e.getKey());
      if (delivery == null) {
        continue;
      }
      if (allowedByThrottle(e.getKey(), e.getValue().throttlePerSec(), update.atMillis())) {
        delivery.listener().onQuote(update);
      }
    }
  }

  /** The latest image on a topic — the logon snapshot path. */
  public synchronized Optional<QuoteUpdate> snapshot(String topic) {
    return Optional.ofNullable(lastImage.get(topic));
  }

  private boolean allowedByThrottle(String subscriptionId, long throttlePerSec, long atMillis) {
    if (throttlePerSec <= 0) {
      return true;
    }
    long[] state = throttleState.computeIfAbsent(subscriptionId, k -> new long[] {atMillis, 0});
    if (atMillis - state[0] >= 1_000) {
      state[0] = atMillis;
      state[1] = 0;
    }
    if (state[1] < throttlePerSec) {
      state[1]++;
      return true;
    }
    return false;
  }
}
