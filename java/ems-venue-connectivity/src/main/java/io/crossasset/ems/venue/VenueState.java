/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue;

/** Connection state of a venue adapter. Per arch-venue-connectivity.md § Failure modes. */
public enum VenueState {
  CONNECTED,
  RECONNECTING,
  DISABLED
}
