/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

/** Result of a {@link RouteManager#route} call. */
public sealed interface RouteResult permits RouteResult.Routed, RouteResult.Rejected {

  /** Route was created and is now in SENT state. */
  record Routed(Route route) implements RouteResult {}

  /** Route creation was rejected; {@code rejectCode} is an EMS-RTE-* catalog code. */
  record Rejected(String requestId, String rejectCode, String message) implements RouteResult {}
}
