/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import io.crossasset.ems.fsm.generated.OrderFsmContext;
import io.crossasset.ems.fsm.generated.OrderFsmState;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable envelope for a staged order. Wraps the FIX-aligned FSM state + context and adds the EMS
 * sub-state and pending-action set needed by the staging layer.
 *
 * <p>Per arch-order-staged.md: OrderFsmContext is treated as logically immutable here — it has no
 * public mutators; only {@code with(...)} creates a new instance.
 */
public final class StagedOrder {

  private final String orderId;
  private final String clOrdId;
  private final long sessionId;
  private final OrderFsmState fsmState;
  private final OrderFsmContext fsmContext;
  private final OrderSubState subState;
  private final Set<String> pendingActions;
  private final long stagedAtMicros;

  public StagedOrder(
      String orderId,
      String clOrdId,
      long sessionId,
      OrderFsmState fsmState,
      OrderFsmContext fsmContext,
      OrderSubState subState,
      Set<String> pendingActions,
      long stagedAtMicros) {
    this.orderId = Objects.requireNonNull(orderId, "orderId");
    this.clOrdId = Objects.requireNonNull(clOrdId, "clOrdId");
    this.sessionId = sessionId;
    this.fsmState = Objects.requireNonNull(fsmState, "fsmState");
    this.fsmContext = Objects.requireNonNull(fsmContext, "fsmContext");
    this.subState = Objects.requireNonNull(subState, "subState");
    this.pendingActions = Set.copyOf(Objects.requireNonNull(pendingActions, "pendingActions"));
    this.stagedAtMicros = stagedAtMicros;
  }

  public String orderId() {
    return orderId;
  }

  public String clOrdId() {
    return clOrdId;
  }

  public long sessionId() {
    return sessionId;
  }

  public OrderFsmState fsmState() {
    return fsmState;
  }

  public OrderFsmContext fsmContext() {
    return fsmContext;
  }

  public OrderSubState subState() {
    return subState;
  }

  public Set<String> pendingActions() {
    return pendingActions;
  }

  public long stagedAtMicros() {
    return stagedAtMicros;
  }

  public boolean isTerminal() {
    return fsmState.isTerminal();
  }

  public boolean isReady() {
    return subState == OrderSubState.READY && pendingActions.isEmpty();
  }
}
