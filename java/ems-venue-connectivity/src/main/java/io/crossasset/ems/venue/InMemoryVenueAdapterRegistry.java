/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe in-memory {@link VenueAdapterRegistry}. */
public final class InMemoryVenueAdapterRegistry implements VenueAdapterRegistry {

  /** Reject code for routing to a venue that lacks the requested capability. */
  public static final String REJECT_CAPABILITY_UNSUPPORTED = "EMS-RTE-1003";

  /** Reject code for routing to an unknown venue. */
  public static final String REJECT_VENUE_NOT_FOUND = "EMS-RTE-5003";

  private final ConcurrentHashMap<String, VenueAdapter> byMic = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, VenueAdapter> byId = new ConcurrentHashMap<>();

  @Override
  public void register(VenueAdapter adapter) {
    VenueRef ref = adapter.venueRef();
    byMic.put(ref.mic(), adapter);
    byId.put(ref.id(), adapter);
  }

  @Override
  public void unregister(String venueId) {
    VenueAdapter removed = byId.remove(venueId);
    if (removed != null) {
      byMic.remove(removed.venueRef().mic(), removed);
    }
  }

  @Override
  public Optional<VenueAdapter> byMic(String mic) {
    return Optional.ofNullable(byMic.get(mic));
  }

  @Override
  public Optional<VenueAdapter> byVenueId(String venueId) {
    return Optional.ofNullable(byId.get(venueId));
  }

  @Override
  public List<VenueAdapter> all() {
    return List.copyOf(byId.values());
  }

  @Override
  public VenueSelection select(String mic, Capability required) {
    VenueAdapter adapter = byMic.get(mic);
    if (adapter == null) {
      return new VenueSelection.NotFound(mic, REJECT_VENUE_NOT_FOUND);
    }
    if (!adapter.supports(required)) {
      return new VenueSelection.Unsupported(mic, required, REJECT_CAPABILITY_UNSUPPORTED);
    }
    return new VenueSelection.Selected(adapter);
  }
}
