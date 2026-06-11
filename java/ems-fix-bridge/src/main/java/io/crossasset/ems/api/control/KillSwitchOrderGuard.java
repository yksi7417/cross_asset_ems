/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.control;

import io.crossasset.ems.aaa.AaaService;
import io.crossasset.ems.aaa.Session;
import io.crossasset.ems.fsm.generated.OrderFsmEvent;
import io.crossasset.ems.oms.AmendFields;
import io.crossasset.ems.oms.AmendResult;
import io.crossasset.ems.oms.CancelResult;
import io.crossasset.ems.oms.MarkReadyResult;
import io.crossasset.ems.oms.OrderRequest;
import io.crossasset.ems.oms.StageResult;
import io.crossasset.ems.oms.StagedOrder;
import io.crossasset.ems.oms.StagedOrderManager;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Order-entry kill-switch guard (task 18.4): the outermost {@link StagedOrderManager} decorator —
 * every surface (REST, FIX, native API, bulk import, baskets) constructs against this, so an
 * engaged scope locks out staging and mark-ready everywhere at once. Cancels and amends pass
 * through (a kill must never prevent risk reduction), as do the router propagation paths (fills on
 * existing positions keep flowing).
 *
 * <p>Fail-secure: a session that cannot be resolved to an identity is treated as locked while any
 * scope is engaged — an unattributable order is exactly what a kill must not let through.
 */
public final class KillSwitchOrderGuard implements StagedOrderManager {

  private final StagedOrderManager delegate;
  private final KillSwitchState state;
  private final AaaService aaa;

  public KillSwitchOrderGuard(StagedOrderManager delegate, KillSwitchState state, AaaService aaa) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.state = Objects.requireNonNull(state, "state");
    this.aaa = Objects.requireNonNull(aaa, "aaa");
  }

  private boolean locked(long sessionId) {
    Optional<Session> session = aaa.sessionInfo(sessionId);
    if (session.isEmpty()) {
      return !state.engagedScopes().isEmpty();
    }
    return state.ordersLocked(session.get().identity().firmId(), session.get().identity().deskId());
  }

  @Override
  public StageResult stage(OrderRequest request) {
    if (locked(request.sessionId())) {
      return new StageResult.Rejected(
          request.requestId(),
          "EMS-ORD-9601",
          "Kill switch engaged — order entry is locked out. Cancels remain allowed.");
    }
    return delegate.stage(request);
  }

  @Override
  public MarkReadyResult markReady(String orderId, long sessionId) {
    if (locked(sessionId)) {
      return new MarkReadyResult.Rejected(
          orderId,
          "EMS-ORD-9601",
          "Kill switch engaged — mark-ready is locked out. Cancels remain allowed.");
    }
    return delegate.markReady(orderId, sessionId);
  }

  // ── Pass-through: risk-reducing and propagation paths stay open ─────────────

  @Override
  public AmendResult amend(String orderId, AmendFields fields, long sessionId) {
    return delegate.amend(orderId, fields, sessionId);
  }

  @Override
  public CancelResult cancel(String orderId, long sessionId) {
    return delegate.cancel(orderId, sessionId);
  }

  @Override
  public void setPendingActionDone(String orderId, String actionRef) {
    delegate.setPendingActionDone(orderId, actionRef);
  }

  @Override
  public Optional<StagedOrder> findOrder(String orderId) {
    return delegate.findOrder(orderId);
  }

  @Override
  public List<StagedOrder> activeOrders() {
    return delegate.activeOrders();
  }

  @Override
  public Optional<StagedOrder> applyOrderFsmEvent(
      String orderId, OrderFsmEvent event, Object payload) {
    return delegate.applyOrderFsmEvent(orderId, event, payload);
  }

  @Override
  public Optional<StagedOrder> markRouting(String orderId) {
    return delegate.markRouting(orderId);
  }
}
