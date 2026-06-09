/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.symbology;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** In-memory billing meter. Counters are additive and never roll back. Thread-safe. */
public class InMemoryBillingMeter implements BillingMeter {

  private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

  @Override
  public void recordSuccess(String identity, SymbologyService.IdType idType) {
    counters.computeIfAbsent(key(identity, idType), k -> new AtomicLong(0)).incrementAndGet();
  }

  @Override
  public long successCount(String identity, SymbologyService.IdType idType) {
    AtomicLong counter = counters.get(key(identity, idType));
    return counter == null ? 0L : counter.get();
  }

  private static String key(String identity, SymbologyService.IdType idType) {
    return identity + ":" + idType.name();
  }
}
