/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix.venue;

import io.crossasset.ems.fix.FixMessage;
import io.crossasset.ems.venue.Capability;
import io.crossasset.ems.venue.VenueRouteRequest;
import java.util.Optional;
import java.util.Set;

/**
 * A venue's FIX dialect (tasks 11.3–11.10): what makes Tradeweb not BrokerTec — order-type
 * restrictions, quantity conventions, mandatory venue tags — encoded once per venue and applied by
 * {@link FixVenueGateway} around its generic session machinery. Certification against the real UAT
 * endpoint exercises THESE rules; until then the {@code FixVenueSimulator} drives them.
 */
public interface VenueDialect {

  /** Dialect identifier (the venue's conventional name). */
  String id();

  /** The venue MIC this dialect speaks for. */
  String mic();

  /** What the venue actually supports (consulted by the router before routing). */
  Set<Capability> capabilities();

  /**
   * Venue-rule validation BEFORE anything hits the wire. Returns the venue-shaped reject reason
   * (the sink reports it as a venue reject) or empty to proceed.
   */
  Optional<String> validate(VenueRouteRequest request);

  /** Append the venue's mandatory/conventional tags to an outbound NewOrderSingle. */
  void customize(FixMessage.Builder builder, VenueRouteRequest request);

  /** A pass-through dialect: no extra rules, no extra tags (the 8.2 generic behavior). */
  static VenueDialect passthrough(String mic) {
    return new VenueDialect() {
      @Override
      public String id() {
        return "generic";
      }

      @Override
      public String mic() {
        return mic;
      }

      @Override
      public Set<Capability> capabilities() {
        return Set.of(
            Capability.SUPPORTS_MARKET,
            Capability.SUPPORTS_LIMIT,
            Capability.SUPPORTS_CANCEL,
            Capability.SUPPORTS_REPLACE);
      }

      @Override
      public Optional<String> validate(VenueRouteRequest request) {
        return Optional.empty();
      }

      @Override
      public void customize(FixMessage.Builder builder, VenueRouteRequest request) {}
    };
  }
}
