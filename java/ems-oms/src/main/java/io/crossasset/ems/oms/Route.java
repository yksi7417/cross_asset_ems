/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import io.crossasset.ems.fsm.generated.RouteFsmContext;
import io.crossasset.ems.fsm.generated.RouteFsmState;
import java.util.Objects;

/**
 * Immutable envelope for a route. Wraps the FIX-aligned Route FSM state + context.
 *
 * <p>Created by {@link RouteManager#route} and updated on each venue event (ack, fill, cancel,
 * replace). Analogous to {@link StagedOrder} at the order layer.
 */
public final class Route {

  private final String routeId;
  private final String orderId;
  private final RouteFsmState fsmState;
  private final RouteFsmContext fsmContext;
  private final long createdAtMicros;

  public Route(
      String routeId,
      String orderId,
      RouteFsmState fsmState,
      RouteFsmContext fsmContext,
      long createdAtMicros) {
    this.routeId = Objects.requireNonNull(routeId, "routeId");
    this.orderId = Objects.requireNonNull(orderId, "orderId");
    this.fsmState = Objects.requireNonNull(fsmState, "fsmState");
    this.fsmContext = Objects.requireNonNull(fsmContext, "fsmContext");
    this.createdAtMicros = createdAtMicros;
  }

  public String routeId() {
    return routeId;
  }

  public String orderId() {
    return orderId;
  }

  public RouteFsmState fsmState() {
    return fsmState;
  }

  public RouteFsmContext fsmContext() {
    return fsmContext;
  }

  public long createdAtMicros() {
    return createdAtMicros;
  }

  public boolean isTerminal() {
    return fsmState.isTerminal();
  }
}
