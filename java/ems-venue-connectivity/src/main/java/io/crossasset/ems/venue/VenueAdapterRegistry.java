/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue;

import java.util.List;
import java.util.Optional;

/**
 * Registry of venue adapters. The router resolves an adapter by MIC (and optionally a required
 * {@link Capability}) before dispatching a route. Per arch-venue-connectivity.md § Capability
 * negotiation.
 */
public interface VenueAdapterRegistry {

  /** Register an adapter, keyed by its {@link VenueRef#mic()} and {@link VenueRef#id()}. */
  void register(VenueAdapter adapter);

  /** Remove the adapter with the given venue id. */
  void unregister(String venueId);

  /** Look up an adapter by MIC. */
  Optional<VenueAdapter> byMic(String mic);

  /** Look up an adapter by its unique venue id. */
  Optional<VenueAdapter> byVenueId(String venueId);

  /** All registered adapters. */
  List<VenueAdapter> all();

  /**
   * Select an adapter for {@code mic} requiring {@code required}. Returns {@link
   * VenueSelection.Selected} on success, {@link VenueSelection.NotFound} for an unknown MIC, or
   * {@link VenueSelection.Unsupported} ({@code EMS-RTE-1003}) if the venue lacks the capability.
   */
  VenueSelection select(String mic, Capability required);
}
