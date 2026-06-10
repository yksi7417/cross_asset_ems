/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import io.crossasset.ems.fsm.generated.OrderFsmEvent;
import io.crossasset.ems.fsm.generated.OrderFsmPayloads;
import io.crossasset.ems.fsm.generated.RouteFsmContext;
import io.crossasset.ems.fsm.generated.RouteFsmEffect;
import io.crossasset.ems.fsm.generated.RouteFsmEvent;
import io.crossasset.ems.fsm.generated.RouteFsmPayloads;
import io.crossasset.ems.fsm.generated.RouteFsmRunner;
import io.crossasset.ems.fsm.generated.RouteFsmState;
import io.crossasset.ems.fsm.generated.TransitionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;

/**
 * Thread-safe in-memory implementation of {@link RouteManager}.
 *
 * <p>Holds a reference to the {@link StagedOrderManager} so fill events can propagate upstream to
 * the order FSM (one-way dependency; SOM is unaware of routes). Route qty validation is best-effort
 * under concurrent routing of the same order — use an external lock if strict atomicity is needed.
 */
public final class InMemoryRouteManager implements RouteManager {

  private final StagedOrderManager som;
  private final ConcurrentHashMap<String, Route> routes = new ConcurrentHashMap<>();
  private final AtomicLong routeIdSeq = new AtomicLong(1);

  public InMemoryRouteManager(StagedOrderManager som) {
    this.som = Objects.requireNonNull(som, "som");
  }

  @Override
  public RouteResult route(RouteRequest request) {
    Optional<StagedOrder> orderOpt = som.findOrder(request.orderId());
    if (orderOpt.isEmpty()) {
      return new RouteResult.Rejected(
          request.requestId(), "EMS-RTE-4001", "Order " + request.orderId() + " not found.");
    }
    StagedOrder order = orderOpt.get();

    if (order.subState() != OrderSubState.READY && order.subState() != OrderSubState.ROUTING) {
      return new RouteResult.Rejected(
          request.requestId(),
          "EMS-RTE-4002",
          "Order "
              + request.orderId()
              + " is not ready for routing (sub-state: "
              + order.subState()
              + ").");
    }

    long openRouteQty =
        routes.values().stream()
            .filter(r -> r.orderId().equals(request.orderId()) && !r.isTerminal())
            .mapToLong(r -> r.fsmContext().leavesQty())
            .sum();
    long orderRemaining = order.fsmContext().leavesQty() - openRouteQty;
    if (request.qty() > orderRemaining) {
      return new RouteResult.Rejected(
          request.requestId(),
          "EMS-RTE-4003",
          "Route qty " + request.qty() + " exceeds order remaining " + orderRemaining + ".");
    }

    String routeId = "EMS-RTE-" + routeIdSeq.getAndIncrement();
    String clOrdId = request.clOrdId() != null ? request.clOrdId() : "CL-" + routeId;

    boolean collision =
        routes.values().stream().anyMatch(r -> clOrdId.equals(r.fsmContext().clOrdId()));
    if (collision) {
      return new RouteResult.Rejected(
          request.requestId(), "EMS-RTE-2005", "ClOrdID " + clOrdId + " already used.");
    }

    RouteFsmContext ctx =
        new RouteFsmContext(
            routeId,
            request.orderId(),
            clOrdId,
            null,
            request.venueMic(),
            order.fsmContext().instrumentId(),
            order.fsmContext().side(),
            request.qty(),
            request.price(),
            0L,
            request.qty(),
            0L,
            order.fsmContext().initialClOrdId(),
            null);

    TransitionResult<RouteFsmState, RouteFsmContext, RouteFsmEffect> tr =
        RouteFsmRunner.transition(RouteFsmState.PENDING, RouteFsmEvent.RouteSent, ctx, null);

    Route route =
        new Route(
            routeId,
            request.orderId(),
            tr.newState(),
            tr.newContext(),
            System.currentTimeMillis() * 1_000L);
    routes.put(routeId, route);

    som.markRouting(request.orderId());

    return new RouteResult.Routed(route);
  }

  @Override
  public RouteEventResult acknowledgeRoute(String routeId) {
    return applyEvent(routeId, RouteFsmEvent.RouteAcknowledged, null, null, null);
  }

  @Override
  public RouteEventResult pendingNewAtVenue(String routeId) {
    return applyEvent(routeId, RouteFsmEvent.RoutePendingNewAtVenue, null, null, null);
  }

  @Override
  public RouteEventResult rejectRoute(String routeId) {
    return applyEvent(routeId, RouteFsmEvent.RouteRejected, null, null, null);
  }

  @Override
  public RouteEventResult cancelRoute(String routeId) {
    return applyEvent(routeId, RouteFsmEvent.RouteCancelRequested, null, null, null);
  }

  @Override
  public RouteEventResult canceledByVenue(String routeId) {
    return applyEvent(routeId, RouteFsmEvent.RouteCanceled, null, null, null);
  }

  @Override
  public RouteEventResult cancelRejectedByVenue(String routeId, int cxlRejReason) {
    return applyEvent(
        routeId,
        RouteFsmEvent.RouteCancelRejected,
        new RouteFsmPayloads.RouteCancelRejectedPayload(cxlRejReason),
        null,
        cxlRejReason);
  }

  @Override
  public RouteEventResult requestReplace(
      String routeId, String newClOrdId, long newQty, @Nullable Long newPrice) {
    return applyEvent(
        routeId,
        RouteFsmEvent.RouteReplaceRequested,
        new RouteFsmPayloads.RouteReplaceRequestedPayload(newClOrdId, newQty, newPrice),
        null,
        null);
  }

  @Override
  public RouteEventResult replacePendingAtVenue(String routeId) {
    return applyEvent(routeId, RouteFsmEvent.RouteReplacePendingAtVenue, null, null, null);
  }

  @Override
  public RouteEventResult replacedByVenue(String routeId) {
    return applyEvent(
        routeId,
        RouteFsmEvent.RouteReplaced,
        new RouteFsmPayloads.RouteReplacedPayload(""),
        null,
        null);
  }

  @Override
  public RouteEventResult replaceRejectedByVenue(String routeId, int cxlRejReason) {
    return applyEvent(
        routeId,
        RouteFsmEvent.RouteReplaceRejected,
        new RouteFsmPayloads.RouteReplaceRejectedPayload(cxlRejReason),
        null,
        cxlRejReason);
  }

  @Override
  public RouteEventResult partialFill(String routeId, long lastQty, long lastPx, String execId) {
    return applyEvent(
        routeId,
        RouteFsmEvent.RoutePartiallyFilled,
        new RouteFsmPayloads.RoutePartiallyFilledPayload(lastQty, lastPx, execId),
        new OrderFsmPayloads.PartialFillPayload(lastQty, lastPx, execId),
        null);
  }

  @Override
  public RouteEventResult fullFill(String routeId, long lastQty, long lastPx, String execId) {
    return applyEvent(
        routeId,
        RouteFsmEvent.RouteFilled,
        new RouteFsmPayloads.RouteFilledPayload(lastQty, lastPx, execId),
        new OrderFsmPayloads.FullFillPayload(lastQty, lastPx, execId),
        null);
  }

  @Override
  public Optional<Route> findRoute(String routeId) {
    return Optional.ofNullable(routes.get(routeId));
  }

  @Override
  public List<Route> findRoutesForOrder(String orderId) {
    List<Route> result = new ArrayList<>();
    for (Route r : routes.values()) {
      if (orderId.equals(r.orderId())) {
        result.add(r);
      }
    }
    return result;
  }

  @Override
  public List<RouteEventResult> cascadeOrderCancel(String orderId) {
    List<RouteEventResult> results = new ArrayList<>();
    for (Route route : findRoutesForOrder(orderId)) {
      if (!route.isTerminal()) {
        results.add(
            applyEvent(route.routeId(), RouteFsmEvent.RouteCancelRequested, null, null, null));
      }
    }
    return results;
  }

  private RouteEventResult applyEvent(
      String routeId,
      RouteFsmEvent event,
      @Nullable Object routePayload,
      @Nullable Object orderFillPayload,
      @Nullable Integer cxlRejReason) {

    Route route = routes.get(routeId);
    if (route == null) {
      return new RouteEventResult.Rejected(
          routeId, "EMS-RTE-5001", "Route " + routeId + " not found.");
    }
    if (route.isTerminal()) {
      return new RouteEventResult.Rejected(
          routeId,
          "EMS-RTE-5002",
          "Route " + routeId + " is in terminal state " + route.fsmState() + ".");
    }

    TransitionResult<RouteFsmState, RouteFsmContext, RouteFsmEffect> tr =
        RouteFsmRunner.transition(route.fsmState(), event, route.fsmContext(), routePayload);
    if (tr.isNoTransition()) {
      return new RouteEventResult.Rejected(
          routeId,
          "EMS-RTE-5002",
          "Route " + routeId + " cannot process " + event + " in state " + route.fsmState() + ".");
    }

    Route updated =
        new Route(
            routeId, route.orderId(), tr.newState(), tr.newContext(), route.createdAtMicros());
    routes.put(routeId, updated);

    for (RouteFsmEffect effect : tr.effects()) {
      if (effect instanceof RouteFsmEffect.EmitEvent emit && "OrderFsm".equals(emit.targetFsm())) {
        dispatchOrderEvent(emit.event(), route.orderId(), orderFillPayload, cxlRejReason);
      }
    }

    return new RouteEventResult.Applied(updated);
  }

  private void dispatchOrderEvent(
      String eventName,
      String orderId,
      @Nullable Object fillPayload,
      @Nullable Integer cxlRejReason) {
    OrderFsmEvent orderEvent;
    Object payload = null;
    switch (eventName) {
      case "ValidationPassed" -> orderEvent = OrderFsmEvent.ValidationPassed;
      case "ValidationFailed" -> orderEvent = OrderFsmEvent.ValidationFailed;
      case "PartialFill" -> {
        orderEvent = OrderFsmEvent.PartialFill;
        payload = fillPayload;
      }
      case "FullFill" -> {
        // A RouteFilled event means this route is done, but the order may still have
        // remaining quantity on other open routes. Downgrade to PartialFill if the
        // order's cumQty + this fill does not yet reach the order's total qty.
        orderEvent = resolveOrderFillEvent(orderId, fillPayload);
        payload = toOrderFillPayload(orderEvent, fillPayload);
      }
      case "OrderExpired" -> orderEvent = OrderFsmEvent.OrderExpired;
      case "ReplaceAccepted" -> orderEvent = OrderFsmEvent.ReplaceAccepted;
      case "ReplaceRejected" -> {
        orderEvent = OrderFsmEvent.ReplaceRejected;
        if (cxlRejReason != null) {
          payload = new OrderFsmPayloads.ReplaceRejectedPayload(cxlRejReason);
        }
      }
      case "CancelRejected" -> {
        orderEvent = OrderFsmEvent.CancelRejected;
        if (cxlRejReason != null) {
          payload = new OrderFsmPayloads.CancelRejectedPayload(cxlRejReason);
        }
      }
      default -> {
        return;
      }
    }
    som.applyOrderFsmEvent(orderId, orderEvent, payload);
  }

  private OrderFsmEvent resolveOrderFillEvent(String orderId, @Nullable Object fillPayload) {
    if (!(fillPayload instanceof OrderFsmPayloads.FullFillPayload fp)) {
      return OrderFsmEvent.FullFill;
    }
    return som.findOrder(orderId)
        .filter(o -> o.fsmContext().cumQty() + fp.lastQty() >= o.fsmContext().orderQty())
        .map(o -> OrderFsmEvent.FullFill)
        .orElse(OrderFsmEvent.PartialFill);
  }

  private Object toOrderFillPayload(OrderFsmEvent event, @Nullable Object fillPayload) {
    if (event == OrderFsmEvent.PartialFill
        && fillPayload instanceof OrderFsmPayloads.FullFillPayload fp) {
      return new OrderFsmPayloads.PartialFillPayload(fp.lastQty(), fp.lastPx(), fp.execId());
    }
    return fillPayload;
  }
}
