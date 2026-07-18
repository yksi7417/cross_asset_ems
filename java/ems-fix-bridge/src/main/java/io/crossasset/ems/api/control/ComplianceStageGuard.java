/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.control;

import io.crossasset.ems.fsm.generated.OrderFsmEvent;
import io.crossasset.ems.oms.AmendFields;
import io.crossasset.ems.oms.AmendResult;
import io.crossasset.ems.oms.CancelResult;
import io.crossasset.ems.oms.MarkReadyResult;
import io.crossasset.ems.oms.OrderRequest;
import io.crossasset.ems.oms.StageResult;
import io.crossasset.ems.oms.StagedOrder;
import io.crossasset.ems.oms.StagedOrderManager;
import io.crossasset.ems.pretrade.compliance.ComplianceDecision;
import io.crossasset.ems.pretrade.compliance.ComplianceGate;
import io.crossasset.ems.pretrade.compliance.ComplianceOperation;
import io.crossasset.ems.pretrade.compliance.ComplianceOutcome;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Compliance gate on the stage path: evaluates every {@link #stage} request through a stage-scoped
 * {@link ComplianceGate} before the order is persisted, and REJECTS on a BLOCK. This is the sibling
 * of {@link ComplianceRouteGuard} for the pre-route entry point — the fat-finger check (10.2) fires
 * only on STAGE/AMEND operations, and the live edge presents orders to the gate only on the ROUTE
 * path, so without this guard the sizing check never fires end-to-end.
 *
 * <p>The stage gate is DELIBERATELY narrower than the route gate: it carries only the idempotent,
 * sizing/list checks that are correct to evaluate at stage time (fat-finger; optionally the list
 * gate). Stateful, consumption-counting checks — MachineGunCheck (per-window route/replace
 * counting) and ShortSaleLocateCheck (locate reservation) — stay ROUTE-only, so staging then
 * routing the same order does not double-count a route or double-consume a locate. FatFingerCheck
 * is stateless (a pure sizing check), so sharing the SAME instance across both gates is correct.
 *
 * <p>Sits INSIDE the kill-switch order guard — a kill still overrides everything — and is
 * constructed by default (opt out with {@code EMS_COMPLIANCE_GATE=0}; see TraderDesktopEdgeMain).
 * Firm/desk are the edge's configured identity, mirroring {@link ComplianceRouteGuard}. Amend and
 * every other {@link StagedOrderManager} method pass through to the delegate unchanged: compliance
 * gates new exposure at stage, never risk reduction or lifecycle propagation.
 */
public final class ComplianceStageGuard implements StagedOrderManager {

  private final StagedOrderManager delegate;
  private final ComplianceGate gate;
  private final String firm;
  private final String desk;

  public ComplianceStageGuard(
      StagedOrderManager delegate, ComplianceGate gate, String firm, String desk) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.gate = Objects.requireNonNull(gate, "gate");
    this.firm = Objects.requireNonNull(firm, "firm");
    this.desk = Objects.requireNonNull(desk, "desk");
  }

  @Override
  public StageResult stage(OrderRequest request) {
    ComplianceOperation operation =
        new ComplianceOperation(
            ComplianceOperation.Kind.STAGE,
            request.sessionId(),
            firm,
            desk,
            "session-" + request.sessionId(),
            request.clOrdId(),
            request.figi(),
            request.side(),
            request.qty(),
            request.price(),
            request.account());
    ComplianceDecision decision = gate.evaluate(operation);
    if (decision.outcome() == ComplianceOutcome.BLOCK) {
      String rationale =
          decision.ruleResults().stream()
              .filter(r -> r.outcome() == ComplianceOutcome.BLOCK)
              .findFirst()
              .map(ComplianceDecision.RuleResult::rationale)
              .orElse("blocked");
      return new StageResult.Rejected(
          request.requestId(), "EMS-CMP-9702", "Compliance gate blocked stage: " + rationale);
    }
    return delegate.stage(request);
  }

  // ── Pass-through: amend/cancel/mark-ready/queries and propagation stay untouched ──

  @Override
  public AmendResult amend(String orderId, AmendFields fields, long sessionId) {
    return delegate.amend(orderId, fields, sessionId);
  }

  @Override
  public CancelResult cancel(String orderId, long sessionId) {
    return delegate.cancel(orderId, sessionId);
  }

  @Override
  public MarkReadyResult markReady(String orderId, long sessionId) {
    return delegate.markReady(orderId, sessionId);
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
