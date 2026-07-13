/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.control;

import io.crossasset.ems.oms.Route;
import io.crossasset.ems.oms.RouteEventResult;
import io.crossasset.ems.oms.RouteManager;
import io.crossasset.ems.oms.RouteRequest;
import io.crossasset.ems.oms.RouteResult;
import io.crossasset.ems.oms.StagedOrder;
import io.crossasset.ems.oms.StagedOrderManager;
import io.crossasset.ems.pretrade.compliance.ComplianceDecision;
import io.crossasset.ems.pretrade.compliance.ComplianceGate;
import io.crossasset.ems.pretrade.compliance.ComplianceOperation;
import io.crossasset.ems.pretrade.compliance.ComplianceOutcome;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Compliance gate on the route path: evaluates every {@link #route} request through the registered
 * pre-trade checks (machine-gun rate limiting first) before the venue dispatch. Sits INSIDE the
 * kill-switch route guard — a kill still overrides everything — and is only constructed when {@code
 * EMS_COMPLIANCE_GATE=1} (default off; see TraderDesktopEdgeMain).
 *
 * <p>v1 scope: the actor key is derived from the staging session (firm/desk resolution arrives with
 * the AAA identity wiring); instrument identity (figi/side) is not available on this path yet, so
 * per-instrument signature keying degrades to per-actor counting — rule 1 (route count per window)
 * is the protection this guard delivers. Route-lifecycle events and queries pass through untouched:
 * compliance gates new exposure, never risk reduction.
 */
public final class ComplianceRouteGuard implements RouteManager {

  private final RouteManager delegate;
  private final ComplianceGate gate;
  private final StagedOrderManager orders;

  public ComplianceRouteGuard(
      RouteManager delegate, ComplianceGate gate, StagedOrderManager orders) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.gate = Objects.requireNonNull(gate, "gate");
    this.orders = Objects.requireNonNull(orders, "orders");
  }

  @Override
  public RouteResult route(RouteRequest request) {
    long sessionId = orders.findOrder(request.orderId()).map(StagedOrder::sessionId).orElse(0L);
    ComplianceOperation operation =
        new ComplianceOperation(
            ComplianceOperation.Kind.ROUTE,
            sessionId,
            "firm-" + sessionId,
            "desk-" + sessionId,
            "session-" + sessionId,
            request.orderId(),
            "?",
            0,
            request.qty(),
            request.price(),
            "?");
    ComplianceDecision decision = gate.evaluate(operation);
    if (decision.outcome() == ComplianceOutcome.BLOCK) {
      String rationale =
          decision.ruleResults().stream()
              .filter(r -> r.outcome() == ComplianceOutcome.BLOCK)
              .findFirst()
              .map(ComplianceDecision.RuleResult::rationale)
              .orElse("blocked");
      return new RouteResult.Rejected(
          request.requestId(), "EMS-CMP-9701", "Compliance gate blocked route: " + rationale);
    }
    return delegate.route(request);
  }

  @Override
  public RouteEventResult acknowledgeRoute(String routeId) {
    return delegate.acknowledgeRoute(routeId);
  }

  @Override
  public RouteEventResult pendingNewAtVenue(String routeId) {
    return delegate.pendingNewAtVenue(routeId);
  }

  @Override
  public RouteEventResult rejectRoute(String routeId) {
    return delegate.rejectRoute(routeId);
  }

  @Override
  public RouteEventResult cancelRoute(String routeId) {
    return delegate.cancelRoute(routeId);
  }

  @Override
  public RouteEventResult canceledByVenue(String routeId) {
    return delegate.canceledByVenue(routeId);
  }

  @Override
  public RouteEventResult cancelRejectedByVenue(String routeId, int cxlRejReason) {
    return delegate.cancelRejectedByVenue(routeId, cxlRejReason);
  }

  @Override
  public RouteEventResult requestReplace(
      String routeId, String newClOrdId, long newQty, @Nullable Long newPrice) {
    return delegate.requestReplace(routeId, newClOrdId, newQty, newPrice);
  }

  @Override
  public RouteEventResult replacePendingAtVenue(String routeId) {
    return delegate.replacePendingAtVenue(routeId);
  }

  @Override
  public RouteEventResult replacedByVenue(String routeId) {
    return delegate.replacedByVenue(routeId);
  }

  @Override
  public RouteEventResult replaceRejectedByVenue(String routeId, int cxlRejReason) {
    return delegate.replaceRejectedByVenue(routeId, cxlRejReason);
  }

  @Override
  public RouteEventResult partialFill(String routeId, long lastQty, long lastPx, String execId) {
    return delegate.partialFill(routeId, lastQty, lastPx, execId);
  }

  @Override
  public RouteEventResult fullFill(String routeId, long lastQty, long lastPx, String execId) {
    return delegate.fullFill(routeId, lastQty, lastPx, execId);
  }

  @Override
  public Optional<Route> findRoute(String routeId) {
    return delegate.findRoute(routeId);
  }

  @Override
  public List<Route> findRoutesForOrder(String orderId) {
    return delegate.findRoutesForOrder(orderId);
  }

  @Override
  public List<Route> activeRoutes() {
    return delegate.activeRoutes();
  }

  @Override
  public List<RouteEventResult> cascadeOrderCancel(String orderId) {
    return delegate.cascadeOrderCancel(orderId);
  }
}
