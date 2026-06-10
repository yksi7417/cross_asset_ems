/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue;

/**
 * Identifies a venue connection: a unique {@code id}, the ISO 10383 {@code mic} the router routes
 * to, and the wire {@code dialect}. Per arch-venue-connectivity.md.
 */
public record VenueRef(String id, String mic, Dialect dialect) {
  public VenueRef {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("venue id required");
    }
    if (mic == null || mic.isBlank()) {
      throw new IllegalArgumentException("venue mic required");
    }
  }
}
