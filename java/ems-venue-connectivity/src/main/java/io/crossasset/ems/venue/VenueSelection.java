/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue;

/**
 * Result of selecting a venue adapter for a route. Capability negotiation per
 * arch-venue-connectivity.md: an unknown MIC yields {@link NotFound}; a known venue lacking the
 * required capability yields {@link Unsupported} ({@code EMS-RTE-1003}).
 */
public sealed interface VenueSelection
    permits VenueSelection.Selected, VenueSelection.Unsupported, VenueSelection.NotFound {

  /** A usable adapter was found and supports the requested capability. */
  record Selected(VenueAdapter adapter) implements VenueSelection {}

  /** The venue exists but does not advertise the required capability. */
  record Unsupported(String mic, Capability required, String rejectCode)
      implements VenueSelection {}

  /** No adapter is registered for the requested MIC. */
  record NotFound(String mic, String rejectCode) implements VenueSelection {}
}
