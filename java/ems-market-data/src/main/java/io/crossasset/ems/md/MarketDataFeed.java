/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.md;

import java.util.Set;

/**
 * The pluggable market-data feed SPI (task 18.12). Provider-agnostic: the Bloomberg Desktop/Server
 * API adapter (18.13) is the first implementation, the internal quote server (9.1, deferred) drops
 * in later, and {@link SimulatedFeed} serves CI and desktop dev-mode. Consumers — the topic bridge
 * that feeds the Perspective desktop via the 8.4 subscription topics, pricing ingestion, click-to-
 * trade — depend only on this interface.
 *
 * <p>Contract: subscriptions are keyed by FIGI (the system symbology) plus a requested {@link
 * MdField} set; ticks delivered to a listener are pre-filtered to that set and a tick with no
 * requested field is not delivered at all. Subscribing is legal in any feed state — a real provider
 * parks the request and replays it on (re)connect, so callers never order operations around
 * connectivity. Feed-wide state surfaces on {@link HealthListener}; per-instrument failures
 * (entitlements, unknown symbol) on {@link Listener#onSubscriptionError}.
 */
public interface MarketDataFeed extends AutoCloseable {

  /** Stable feed identity ("bloomberg-dapi", "sim") — appears on every tick and health event. */
  String feedId();

  /** Connect / begin delivering. Idempotent. */
  void start();

  /** Disconnect and stop delivering. Idempotent; subscriptions are dropped. */
  @Override
  void close();

  /** Current feed health. */
  FeedHealth health();

  /** Register for health transitions. The listener immediately receives the current state. */
  void addHealthListener(HealthListener listener);

  /**
   * Subscribe to one instrument's fields. Returns the subscription ID used to unsubscribe. Ticks
   * arrive on {@code listener} filtered to {@code fields}.
   */
  String subscribe(String figi, Set<MdField> fields, Listener listener);

  /** Cancel a subscription. Returns false if the ID is unknown (already canceled, never issued). */
  boolean unsubscribe(String subscriptionId);

  /** Tick + per-subscription error callbacks. */
  interface Listener {

    /** One row delta, filtered to the subscription's field set. */
    void onTick(MdTick tick);

    /**
     * The subscription failed provider-side (entitlement denied, unknown symbol). The subscription
     * stays registered — a re-entitled symbol resumes ticking without a re-subscribe.
     */
    default void onSubscriptionError(String figi, String code, String message) {}
  }

  /** Feed-level health callback. */
  interface HealthListener {
    void onHealth(String feedId, FeedHealth health);
  }
}
