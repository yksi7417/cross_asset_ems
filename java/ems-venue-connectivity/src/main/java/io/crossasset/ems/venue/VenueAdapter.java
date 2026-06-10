/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue;

import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * One adapter per venue connection. Isolates the venue's wire dialect (FIX/binary/REST/BLPAPI)
 * behind a single internal protocol: the router calls {@link #submit}/{@link #cancel}/{@link
 * #replace}, and the adapter surfaces venue responses through its {@link VenueEventSink}.
 *
 * <p>Per arch-venue-connectivity.md. Venue-session concerns (logon, heartbeat, sequence reset at
 * the venue's protocol level) are the adapter's responsibility and are independent of the internal
 * session layer (arch-sequence-recovery.md).
 */
public interface VenueAdapter {

  /** Identity of this venue connection. */
  VenueRef venueRef();

  /** Capabilities advertised at startup; consulted by the router before routing. */
  Set<Capability> capabilities();

  /** Current connection state. */
  VenueState state();

  /** True if the adapter advertises the given capability. */
  default boolean supports(Capability capability) {
    return capabilities().contains(capability);
  }

  /** Submit a child order to the venue. */
  void submit(VenueRouteRequest request);

  /** Request cancellation of a previously submitted route. */
  void cancel(String routeId);

  /** Request a replace (amend qty/price) of a previously submitted route. */
  void replace(String routeId, String newClOrdId, long newQty, @Nullable Long newPrice);
}
