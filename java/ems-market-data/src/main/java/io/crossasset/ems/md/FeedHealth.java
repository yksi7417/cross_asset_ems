/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.md;

import java.util.Objects;

/**
 * Feed-level health (task 18.12). Session resilience and provider-wide failures surface here
 * (Bloomberg session down, reconnect in progress); per-instrument problems (entitlement denial on
 * one FIGI) surface on {@link MarketDataFeed.Listener#onSubscriptionError} instead, because one
 * denied symbol must not mark the whole feed unhealthy.
 *
 * @param status the coarse state the desktop badge shows
 * @param detail human-readable cause ("session lost, reconnecting", "logon rejected")
 * @param atMillis when this state was entered (epoch millis)
 */
public record FeedHealth(Status status, String detail, long atMillis) {

  /** Coarse feed state. DEGRADED = connected but impaired (gaps, partial entitlements). */
  public enum Status {
    CONNECTING,
    UP,
    DEGRADED,
    DOWN
  }

  public FeedHealth {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(detail, "detail");
  }

  public static FeedHealth of(Status status, String detail, long atMillis) {
    return new FeedHealth(status, detail, atMillis);
  }
}
