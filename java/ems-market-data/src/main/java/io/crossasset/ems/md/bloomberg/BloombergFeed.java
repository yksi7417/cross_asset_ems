/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.md.bloomberg;

import io.crossasset.ems.md.FeedHealth;
import io.crossasset.ems.md.MarketDataFeed;
import io.crossasset.ems.md.MdField;
import io.crossasset.ems.md.MdTick;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bloomberg market-data adapter (task 18.13): {@code //blp/mktdata} subscriptions via Desktop API
 * or Server API per {@link BloombergConfig}, mapped onto the 18.12 {@link MarketDataFeed} SPI.
 *
 * <p>Session resilience: subscriptions are parked in the feed and (re-)issued on every {@code
 * onSessionUp}, so a terminal restart or SAPI failover replays the desk's watchlist without caller
 * involvement (the SPI's subscribe-in-any-state rule). Entitlement/permission failures surface
 * twice, deliberately: per-instrument on {@link Listener#onSubscriptionError} (the denied symbol's
 * row badge) and feed-wide as {@code DEGRADED} health while any subscription is failing (the
 * desktop's feed badge) — recovering to {@code UP} when the provider later acknowledges.
 *
 * <p>Field values arrive as mnemonic-keyed doubles; price-kind fields scale into the system's
 * fixed-point longs by {@code priceScale}, size-kind fields round to raw counts. Runtime requires
 * the desk's Bloomberg terminal or SAPI subscription (via {@link ReflectiveBlpapiDriver}); CI runs
 * this same class against a fake driver.
 */
public final class BloombergFeed implements MarketDataFeed, BlpapiDriver.DriverEvents {

  private record Sub(long correlationId, String figi, Set<MdField> fields, Listener listener) {}

  private final BloombergConfig config;
  private final BlpapiDriver driver;
  private final Map<Long, Sub> subsByCorrelation = new LinkedHashMap<>();
  private final Map<String, Long> correlationBySubId = new LinkedHashMap<>();
  private final Set<Long> failedCorrelations = new HashSet<>();
  private final List<HealthListener> healthListeners = new ArrayList<>();
  private final AtomicLong correlationSeq = new AtomicLong(1);
  private FeedHealth health = FeedHealth.of(FeedHealth.Status.DOWN, "not started", 0);
  private boolean sessionUp;
  private boolean started;

  public BloombergFeed(BloombergConfig config, BlpapiDriver driver) {
    this.config = Objects.requireNonNull(config, "config");
    this.driver = Objects.requireNonNull(driver, "driver");
  }

  @Override
  public String feedId() {
    return config.feedId();
  }

  @Override
  public synchronized void start() {
    if (started) {
      return;
    }
    started = true;
    setHealth(FeedHealth.Status.CONNECTING, "connecting " + config.host() + ":" + config.port(), 0);
    driver.connect(config, this);
  }

  @Override
  public synchronized void close() {
    if (!started) {
      return;
    }
    started = false;
    sessionUp = false;
    driver.disconnect();
    subsByCorrelation.clear();
    correlationBySubId.clear();
    failedCorrelations.clear();
    setHealth(FeedHealth.Status.DOWN, "closed", health.atMillis());
  }

  @Override
  public synchronized FeedHealth health() {
    return health;
  }

  @Override
  public synchronized void addHealthListener(HealthListener listener) {
    healthListeners.add(Objects.requireNonNull(listener, "listener"));
    listener.onHealth(feedId(), health);
  }

  @Override
  public synchronized String subscribe(String figi, Set<MdField> fields, Listener listener) {
    Objects.requireNonNull(figi, "figi");
    Objects.requireNonNull(listener, "listener");
    long correlationId = correlationSeq.getAndIncrement();
    Sub sub = new Sub(correlationId, figi, Set.copyOf(fields), listener);
    subsByCorrelation.put(correlationId, sub);
    String subId = "MD-" + feedId() + "-" + correlationId;
    correlationBySubId.put(subId, correlationId);
    if (sessionUp) {
      issue(sub);
    }
    return subId;
  }

  @Override
  public synchronized boolean unsubscribe(String subscriptionId) {
    Long correlationId = correlationBySubId.remove(subscriptionId);
    if (correlationId == null) {
      return false;
    }
    subsByCorrelation.remove(correlationId);
    failedCorrelations.remove(correlationId);
    if (sessionUp) {
      driver.unsubscribe(correlationId);
    }
    return true;
  }

  // ── Driver events (BLPAPI event stream, already demuxed) ─────────────────────

  @Override
  public synchronized void onSessionUp() {
    sessionUp = true;
    failedCorrelations.clear();
    setHealth(FeedHealth.Status.UP, "session up (" + config.mode() + ")", health.atMillis());
    for (Sub sub : subsByCorrelation.values()) {
      issue(sub);
    }
  }

  @Override
  public synchronized void onSessionDown(String reason) {
    sessionUp = false;
    if (started) {
      setHealth(FeedHealth.Status.DOWN, reason, health.atMillis());
    }
  }

  @Override
  public synchronized void onSubscriptionStarted(long correlationId) {
    if (failedCorrelations.remove(correlationId)
        && failedCorrelations.isEmpty()
        && sessionUp
        && health.status() == FeedHealth.Status.DEGRADED) {
      setHealth(FeedHealth.Status.UP, "all subscriptions healthy", health.atMillis());
    }
  }

  @Override
  public synchronized void onSubscriptionFailure(
      long correlationId, String category, String message) {
    Sub sub = subsByCorrelation.get(correlationId);
    if (sub == null) {
      return;
    }
    failedCorrelations.add(correlationId);
    sub.listener().onSubscriptionError(sub.figi(), category, message);
    if (sessionUp) {
      setHealth(
          FeedHealth.Status.DEGRADED,
          failedCorrelations.size() + " subscription(s) failing (" + category + ")",
          health.atMillis());
    }
  }

  @Override
  public synchronized void onTick(
      long correlationId, Map<String, Double> mnemonicValues, long atMillis) {
    Sub sub = subsByCorrelation.get(correlationId);
    if (sub == null) {
      return;
    }
    Map<MdField, Long> values = new LinkedHashMap<>();
    for (MdField field : sub.fields()) {
      Double raw = mnemonicValues.get(config.fieldMap().get(field));
      if (raw == null) {
        continue;
      }
      long value =
          field.isPrice() ? Math.round(raw * config.priceScale()) : Math.round(raw.doubleValue());
      values.put(field, value);
    }
    if (!values.isEmpty()) {
      sub.listener().onTick(new MdTick(feedId(), sub.figi(), values, atMillis));
    }
  }

  // ── Internals ────────────────────────────────────────────────────────────────

  /** Subscription topic + mnemonics for one sub; fields without a mapping are not requested. */
  private void issue(Sub sub) {
    List<String> mnemonics = new ArrayList<>();
    for (MdField field : sub.fields()) {
      String mnemonic = config.fieldMap().get(field);
      if (mnemonic != null) {
        mnemonics.add(mnemonic);
      }
    }
    driver.subscribe(sub.correlationId(), config.symbologyPrefix() + sub.figi(), mnemonics);
  }

  private void setHealth(FeedHealth.Status status, String detail, long atMillis) {
    health = FeedHealth.of(status, detail, atMillis);
    for (HealthListener listener : healthListeners) {
      listener.onHealth(feedId(), health);
    }
  }
}
