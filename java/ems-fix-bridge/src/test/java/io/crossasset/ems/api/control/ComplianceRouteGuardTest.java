/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.crossasset.ems.fsm.generated.OrderFsmEvent;
import io.crossasset.ems.oms.AmendFields;
import io.crossasset.ems.oms.AmendResult;
import io.crossasset.ems.oms.CancelResult;
import io.crossasset.ems.oms.MarkReadyResult;
import io.crossasset.ems.oms.OrderRequest;
import io.crossasset.ems.oms.Route;
import io.crossasset.ems.oms.RouteEventResult;
import io.crossasset.ems.oms.RouteManager;
import io.crossasset.ems.oms.RouteRequest;
import io.crossasset.ems.oms.RouteResult;
import io.crossasset.ems.oms.StageResult;
import io.crossasset.ems.oms.StagedOrder;
import io.crossasset.ems.oms.StagedOrderManager;
import io.crossasset.ems.pretrade.compliance.ComplianceGate;
import io.crossasset.ems.pretrade.compliance.MachineGunCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** ComplianceRouteGuard: machine-gun engagement on route(), pass-through everywhere else. */
class ComplianceRouteGuardTest {

  /** Records invocations; every mutator returns a canned Rejected so no Route is needed. */
  static final class FakeRouteManager implements RouteManager {
    final List<String> calls = new ArrayList<>();

    @Override
    public RouteResult route(RouteRequest request) {
      calls.add("route:" + request.requestId());
      return new RouteResult.Rejected(request.requestId(), "FAKE", "delegate reached");
    }

    @Override
    public RouteEventResult acknowledgeRoute(String routeId) {
      calls.add("ack:" + routeId);
      return new RouteEventResult.Rejected(routeId, "FAKE", "delegate reached");
    }

    @Override
    public RouteEventResult pendingNewAtVenue(String routeId) {
      return new RouteEventResult.Rejected(routeId, "FAKE", "x");
    }

    @Override
    public RouteEventResult rejectRoute(String routeId) {
      return new RouteEventResult.Rejected(routeId, "FAKE", "x");
    }

    @Override
    public RouteEventResult cancelRoute(String routeId) {
      calls.add("cancel:" + routeId);
      return new RouteEventResult.Rejected(routeId, "FAKE", "delegate reached");
    }

    @Override
    public RouteEventResult canceledByVenue(String routeId) {
      return new RouteEventResult.Rejected(routeId, "FAKE", "x");
    }

    @Override
    public RouteEventResult cancelRejectedByVenue(String routeId, int cxlRejReason) {
      return new RouteEventResult.Rejected(routeId, "FAKE", "x");
    }

    @Override
    public RouteEventResult requestReplace(
        String routeId, String newClOrdId, long newQty, Long newPrice) {
      return new RouteEventResult.Rejected(routeId, "FAKE", "x");
    }

    @Override
    public RouteEventResult replacePendingAtVenue(String routeId) {
      return new RouteEventResult.Rejected(routeId, "FAKE", "x");
    }

    @Override
    public RouteEventResult replacedByVenue(String routeId) {
      return new RouteEventResult.Rejected(routeId, "FAKE", "x");
    }

    @Override
    public RouteEventResult replaceRejectedByVenue(String routeId, int cxlRejReason) {
      return new RouteEventResult.Rejected(routeId, "FAKE", "x");
    }

    @Override
    public RouteEventResult partialFill(String routeId, long lastQty, long lastPx, String execId) {
      return new RouteEventResult.Rejected(routeId, "FAKE", "x");
    }

    @Override
    public RouteEventResult fullFill(String routeId, long lastQty, long lastPx, String execId) {
      return new RouteEventResult.Rejected(routeId, "FAKE", "x");
    }

    @Override
    public Optional<Route> findRoute(String routeId) {
      calls.add("findRoute:" + routeId);
      return Optional.empty();
    }

    @Override
    public List<Route> findRoutesForOrder(String orderId) {
      return List.of();
    }

    @Override
    public List<Route> activeRoutes() {
      return List.of();
    }

    @Override
    public List<RouteEventResult> cascadeOrderCancel(String orderId) {
      return List.of();
    }
  }

  /** findOrder returns empty (guard defaults sessionId to 0); everything else is inert. */
  static final class FakeStagedOrderManager implements StagedOrderManager {
    @Override
    public StageResult stage(OrderRequest request) {
      return new StageResult.Rejected(request.requestId(), "FAKE", "x");
    }

    @Override
    public AmendResult amend(String orderId, AmendFields fields, long sessionId) {
      return new AmendResult.Rejected(orderId, "FAKE", "x");
    }

    @Override
    public CancelResult cancel(String orderId, long sessionId) {
      return new CancelResult.Rejected(orderId, "FAKE", "x");
    }

    @Override
    public MarkReadyResult markReady(String orderId, long sessionId) {
      return new MarkReadyResult.Rejected(orderId, "FAKE", "x");
    }

    @Override
    public void setPendingActionDone(String orderId, String actionRef) {}

    @Override
    public Optional<StagedOrder> findOrder(String orderId) {
      return Optional.empty();
    }

    @Override
    public List<StagedOrder> activeOrders() {
      return List.of();
    }

    @Override
    public Optional<StagedOrder> applyOrderFsmEvent(
        String orderId, OrderFsmEvent event, Object payload) {
      return Optional.empty();
    }

    @Override
    public Optional<StagedOrder> markRouting(String orderId) {
      return Optional.empty();
    }
  }

  private static ComplianceRouteGuard guard(FakeRouteManager delegate, long[] clock) {
    MachineGunCheck.Policy policy = new MachineGunCheck.Policy(1000L, 2, 1_000_000_000L, 100);
    ComplianceGate gate =
        new ComplianceGate(List.of(new MachineGunCheck(policy, () -> clock[0])));
    return new ComplianceRouteGuard(delegate, gate, new FakeStagedOrderManager());
  }

  private static RouteRequest req(String id) {
    return new RouteRequest(id, "O1", "XNAS", 100L, null, null);
  }

  @Test
  void thirdRapidRouteIsBlockedAndNeverReachesDelegate() {
    FakeRouteManager delegate = new FakeRouteManager();
    long[] clock = {1_000L};
    ComplianceRouteGuard guard = guard(delegate, clock);

    assertInstanceOf(RouteResult.Rejected.class, guard.route(req("R1"))); // FAKE = delegate
    assertInstanceOf(RouteResult.Rejected.class, guard.route(req("R2")));
    assertEquals(List.of("route:R1", "route:R2"), delegate.calls);

    RouteResult third = guard.route(req("R3"));
    RouteResult.Rejected rejected = assertInstanceOf(RouteResult.Rejected.class, third);
    assertEquals("EMS-CMP-9701", rejected.rejectCode());
    assertTrue(rejected.message().contains("machine_gun_route_count_exceeded"));
    assertEquals(List.of("route:R1", "route:R2"), delegate.calls); // no third delegate call
  }

  @Test
  void routesOutsideTheWindowPassAgain() {
    FakeRouteManager delegate = new FakeRouteManager();
    long[] clock = {1_000L};
    ComplianceRouteGuard guard = guard(delegate, clock);
    guard.route(req("R1"));
    guard.route(req("R2"));
    clock[0] += 10_000L; // window expired
    guard.route(req("R4"));
    assertEquals(List.of("route:R1", "route:R2", "route:R4"), delegate.calls);
  }

  @Test
  void lifecycleAndQueriesPassThrough() {
    FakeRouteManager delegate = new FakeRouteManager();
    ComplianceRouteGuard guard = guard(delegate, new long[] {1_000L});
    guard.acknowledgeRoute("RT-1");
    guard.cancelRoute("RT-1");
    guard.findRoute("RT-1");
    assertEquals(List.of("ack:RT-1", "cancel:RT-1", "findRoute:RT-1"), delegate.calls);
  }
}
