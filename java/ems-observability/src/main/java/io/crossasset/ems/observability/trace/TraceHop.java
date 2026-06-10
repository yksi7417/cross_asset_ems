/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.observability.trace;

/**
 * The hops a single order traverses from the client FIX edge to the venue, in order. The
 * distributed-trace verification (task 13.5) asserts one trace ID is carried unbroken across every
 * hop of this chain (per arch-observability.md and arch-fix-api-bridge.md § Distributed tracing).
 */
public enum TraceHop {
  FIX_IN,
  VALIDATE,
  STAGE,
  ROUTE,
  VENUE_OUT
}
