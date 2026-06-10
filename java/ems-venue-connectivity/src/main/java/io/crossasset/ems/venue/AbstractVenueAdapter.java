/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base for concrete venue adapters. Manages connection state and holds the {@link VenueEventSink}
 * and shadow-mode flag, leaving only the wire translation ({@link #submit}/{@link #cancel}/{@link
 * #replace}) to subclasses.
 *
 * <p><b>Shadow mode</b> (arch-venue-connectivity.md): when {@code shadow} is true, outbound traffic
 * is discarded and inbound is driven from the replay log instead of the venue — making replay
 * deterministic and preventing accidental routing during regression runs. Subclasses should consult
 * {@link #isShadow()} before touching the wire.
 */
public abstract class AbstractVenueAdapter implements VenueAdapter {

  private final VenueRef venueRef;
  private final Set<Capability> capabilities;
  private final VenueEventSink sink;
  private final boolean shadow;
  private final AtomicReference<VenueState> state = new AtomicReference<>(VenueState.DISABLED);

  protected AbstractVenueAdapter(
      VenueRef venueRef, Set<Capability> capabilities, VenueEventSink sink, boolean shadow) {
    this.venueRef = Objects.requireNonNull(venueRef, "venueRef");
    this.capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
    this.sink = Objects.requireNonNull(sink, "sink");
    this.shadow = shadow;
  }

  @Override
  public final VenueRef venueRef() {
    return venueRef;
  }

  @Override
  public final Set<Capability> capabilities() {
    return capabilities;
  }

  @Override
  public final VenueState state() {
    return state.get();
  }

  /** The sink to surface venue events back toward the router. */
  protected final VenueEventSink sink() {
    return sink;
  }

  /** True if the adapter is in shadow mode (no real wire traffic). */
  protected final boolean isShadow() {
    return shadow;
  }

  /** Transition the connection state (e.g. on logon, disconnect, disable). */
  protected final void setState(VenueState newState) {
    state.set(Objects.requireNonNull(newState, "newState"));
  }
}
