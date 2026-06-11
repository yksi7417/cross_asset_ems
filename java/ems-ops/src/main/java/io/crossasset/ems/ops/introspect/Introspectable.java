/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.ops.introspect;

import java.util.List;
import java.util.Map;

/**
 * The standardized read-side introspection surface every component exposes (task 14.1,
 * arch-jmx-introspection.md): JMX-like in spirit, but a plain SPI that rides the same transport as
 * everything else — diagnose live systems without a debugger. The privileged write surface (event
 * injection, state overrides) is task 14.2 and layers on top of the registry.
 */
public interface Introspectable {

  /** Stable component identity (e.g. {@code oms.staged-order-manager}). */
  String componentId();

  /** Schema of the inspectable state: field name → type label. Stable and versioned. */
  Map<String, String> describeState();

  /** Current values for every described field, rendered deterministically as strings. */
  Map<String, String> dumpState();

  /** Counters/gauges this component exposes. */
  List<Metric> listMetrics();

  /** Liveness verdict; YELLOW = degraded but functional, RED = do not route through. */
  Health health();

  /** One metric sample. */
  record Metric(String name, Kind kind, long value) {
    public enum Kind {
      COUNTER,
      GAUGE
    }
  }

  /** Component health with a human-readable reason when not GREEN. */
  record Health(Status status, String reason) {
    public enum Status {
      GREEN,
      YELLOW,
      RED
    }

    public static Health green() {
      return new Health(Status.GREEN, "");
    }
  }
}
