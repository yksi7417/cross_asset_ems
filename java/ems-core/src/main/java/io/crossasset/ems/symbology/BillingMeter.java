/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.symbology;

/**
 * Counts successful licensed identifier accesses for per-seat / per-call billing.
 *
 * <p>Only successful (granted) accesses are billed; denied attempts are not counted.
 */
public interface BillingMeter {

  /** Increments the success counter for {@code identity} × {@code idType}. */
  void recordSuccess(String identity, SymbologyService.IdType idType);

  /** Returns the number of successful accesses recorded for {@code identity} × {@code idType}. */
  long successCount(String identity, SymbologyService.IdType idType);
}
