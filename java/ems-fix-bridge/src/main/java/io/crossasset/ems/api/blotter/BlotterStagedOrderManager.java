/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.blotter;

import io.crossasset.ems.fsm.generated.OrderFsmEvent;
import io.crossasset.ems.oms.AmendFields;
import io.crossasset.ems.oms.AmendResult;
import io.crossasset.ems.oms.CancelResult;
import io.crossasset.ems.oms.MarkReadyResult;
import io.crossasset.ems.oms.OrderRequest;
import io.crossasset.ems.oms.StageResult;
import io.crossasset.ems.oms.StagedOrder;
import io.crossasset.ems.oms.StagedOrderManager;
import java.util.Objects;
import java.util.Optional;

/**
 * Order-layer blotter decorator (task 18.1): delegates every operation and publishes the updated
 * order row after each successful mutation. {@link #applyOrderFsmEvent} and {@link #markRouting}
 * are the router's propagation paths — wrapping them means venue fills update the parent order's
 * blotter row with no extra wiring (construct the route manager over <em>this</em> instance).
 */
public final class BlotterStagedOrderManager implements StagedOrderManager {

  private final StagedOrderManager delegate;
  private final BlotterPublisher blotter;

  public BlotterStagedOrderManager(StagedOrderManager delegate, BlotterPublisher blotter) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.blotter = Objects.requireNonNull(blotter, "blotter");
  }

  @Override
  public StageResult stage(OrderRequest request) {
    StageResult result = delegate.stage(request);
    if (result instanceof StageResult.Accepted accepted) {
      blotter.publishOrder(accepted.order());
    }
    return result;
  }

  @Override
  public AmendResult amend(String orderId, AmendFields fields, long sessionId) {
    AmendResult result = delegate.amend(orderId, fields, sessionId);
    if (result instanceof AmendResult.Amended amended) {
      blotter.publishOrder(amended.order());
    }
    return result;
  }

  @Override
  public CancelResult cancel(String orderId, long sessionId) {
    CancelResult result = delegate.cancel(orderId, sessionId);
    if (!(result instanceof CancelResult.Rejected)) {
      delegate.findOrder(orderId).ifPresent(blotter::publishOrder);
    }
    return result;
  }

  @Override
  public MarkReadyResult markReady(String orderId, long sessionId) {
    MarkReadyResult result = delegate.markReady(orderId, sessionId);
    if (!(result instanceof MarkReadyResult.Rejected)) {
      delegate.findOrder(orderId).ifPresent(blotter::publishOrder);
    }
    return result;
  }

  @Override
  public void setPendingActionDone(String orderId, String actionRef) {
    delegate.setPendingActionDone(orderId, actionRef);
    delegate.findOrder(orderId).ifPresent(blotter::publishOrder);
  }

  @Override
  public Optional<StagedOrder> findOrder(String orderId) {
    return delegate.findOrder(orderId);
  }

  @Override
  public java.util.List<StagedOrder> activeOrders() {
    return delegate.activeOrders();
  }

  @Override
  public Optional<StagedOrder> applyOrderFsmEvent(
      String orderId, OrderFsmEvent event, Object payload) {
    Optional<StagedOrder> result = delegate.applyOrderFsmEvent(orderId, event, payload);
    result.ifPresent(blotter::publishOrder);
    return result;
  }

  @Override
  public Optional<StagedOrder> markRouting(String orderId) {
    Optional<StagedOrder> result = delegate.markRouting(orderId);
    result.ifPresent(blotter::publishOrder);
    return result;
  }
}
