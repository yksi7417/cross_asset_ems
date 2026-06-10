/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

/** Result of a venue-callback operation on an existing route (ack, fill, cancel, replace). */
public sealed interface RouteEventResult
    permits RouteEventResult.Applied, RouteEventResult.Rejected {

  /** Event was applied; {@code route} reflects the updated state. */
  record Applied(Route route) implements RouteEventResult {}

  /** Event was rejected; {@code rejectCode} is an EMS-RTE-* catalog code. */
  record Rejected(String routeId, String rejectCode, String message) implements RouteEventResult {}
}
