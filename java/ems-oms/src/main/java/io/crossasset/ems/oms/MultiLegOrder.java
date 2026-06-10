/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import io.crossasset.ems.fsm.generated.MultiLegFsmContext;
import io.crossasset.ems.fsm.generated.MultiLegFsmState;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Immutable envelope for a multi-leg / package order. Wraps the MultiLeg FSM state + context and
 * the per-leg views. Analogous to {@link StagedOrder} at the order layer.
 *
 * <p>Per arch-multileg.md, task 7.4.
 */
public final class MultiLegOrder {

  private final String orderId;
  private final String clOrdId;
  private final long sessionId;
  private final MultiLegFsmState fsmState;
  private final MultiLegFsmContext fsmContext;
  private final @Nullable String sequencePolicy;
  private final List<OrderLeg> legs;
  private final long stagedAtMicros;

  public MultiLegOrder(
      String orderId,
      String clOrdId,
      long sessionId,
      MultiLegFsmState fsmState,
      MultiLegFsmContext fsmContext,
      @Nullable String sequencePolicy,
      List<OrderLeg> legs,
      long stagedAtMicros) {
    this.orderId = Objects.requireNonNull(orderId, "orderId");
    this.clOrdId = Objects.requireNonNull(clOrdId, "clOrdId");
    this.sessionId = sessionId;
    this.fsmState = Objects.requireNonNull(fsmState, "fsmState");
    this.fsmContext = Objects.requireNonNull(fsmContext, "fsmContext");
    this.sequencePolicy = sequencePolicy;
    this.legs = List.copyOf(Objects.requireNonNull(legs, "legs"));
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

  public MultiLegFsmState fsmState() {
    return fsmState;
  }

  public MultiLegFsmContext fsmContext() {
    return fsmContext;
  }

  public @Nullable String sequencePolicy() {
    return sequencePolicy;
  }

  public List<OrderLeg> legs() {
    return legs;
  }

  public long stagedAtMicros() {
    return stagedAtMicros;
  }

  public boolean isTerminal() {
    return fsmState.isTerminal();
  }
}
