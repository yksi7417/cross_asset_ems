/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue;

/** Wire dialect a venue adapter speaks. Per arch-venue-connectivity.md. */
public enum Dialect {
  FIX,
  BIN,
  REST,
  BLPAPI
}
