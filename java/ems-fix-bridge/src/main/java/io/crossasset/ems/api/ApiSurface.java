/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api;

import io.crossasset.ems.aaa.AaaService;
import io.crossasset.ems.oms.AmendFields;
import io.crossasset.ems.oms.AmendResult;
import io.crossasset.ems.oms.CancelResult;
import io.crossasset.ems.oms.MarkReadyResult;
import io.crossasset.ems.oms.OrderRequest;
import io.crossasset.ems.oms.RouteEventResult;
import io.crossasset.ems.oms.RouteManager;
import io.crossasset.ems.oms.RouteRequest;
import io.crossasset.ems.oms.RouteResult;
import io.crossasset.ems.oms.StageResult;
import io.crossasset.ems.oms.StagedOrderManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The API session surface (task 8.4): one typed operation set over the AAA-authenticated, sequenced
 * session channel. Native clients hand-shake by calling {@link AaaService#logon} (which registers
 * the resumable channel per 8.9) and then drive everything through {@link #execute}; REST/WS (8.10)
 * and FIX (8.1) are edge bindings onto this same surface.
 *
 * <p>Envelope semantics per arch-api-first.md: {@code requestId} is the idempotency key — a replay
 * returns the cached response without re-executing; {@code sessionSeq} is deduped/gap-checked by
 * the AAA session channel; every item produces a position-matched {@link ItemResult}; successful
 * mutations publish typed events on {@code orders} (the blotter stream) and {@code order.{orderId}}
 * topics, which clients consume via SUBSCRIBE with cursor resume.
 *
 * <p>v1 batch option semantics are CONTINUE-on-error (every item attempted); all-or-nothing and
 * STOP modes land with task 8.5.
 */
public final class ApiSurface {

  private final AaaService aaa;
  private final StagedOrderManager som;
  private final RouteManager routeManager;
  private final SubscriptionRegistry subscriptions;
  private final ApiEventSink eventSink;
  private final ConcurrentHashMap<String, ApiResponse> idempotencyCache = new ConcurrentHashMap<>();

  /** Blotter topic carrying every order event. */
  public static final String TOPIC_ORDERS = "orders";

  public ApiSurface(
      AaaService aaa,
      StagedOrderManager som,
      RouteManager routeManager,
      SubscriptionRegistry subscriptions,
      ApiEventSink eventSink) {
    this.aaa = aaa;
    this.som = som;
    this.routeManager = routeManager;
    this.subscriptions = subscriptions;
    this.eventSink = eventSink;
  }

  /** Execute one batch request. Never throws on bad input — every failure is an ItemResult. */
  public ApiResponse execute(ApiRequest request) {
    ApiResponse cached = idempotencyCache.get(request.requestId());
    if (cached != null) {
      return cached;
    }
    if (aaa.sessionInfo(request.sessionId()).isEmpty()) {
      return reject(request, "EMS-SES-1002", "Session not found or expired.");
    }
    var seqIssue = aaa.checkIncoming(request.sessionId(), request.sessionSeq());
    if (seqIssue.isPresent()) {
      return reject(request, "EMS-SES-2001", "Sequence not accepted: " + seqIssue.get());
    }
    if (!request.options().partialOk() && request.operation() != ApiOperation.STAGE_ORDERS) {
      return reject(
          request,
          "EMS-ORD-3003",
          "all-or-nothing (partial_ok=false) is only supported for STAGE_ORDERS; "
              + request.operation()
              + " has no compensating action.");
    }

    List<ItemResult> results = new ArrayList<>(request.items().size());
    boolean stopped = false;
    for (ApiItem item : request.items()) {
      if (stopped) {
        results.add(ItemResult.deferred("Not attempted: prior item rejected (on_error=STOP)."));
        continue;
      }
      ItemResult result = executeItem(request, item);
      results.add(result);
      if (result.status() == ItemResult.Status.REJECTED
          && request.options().onError() == BatchOptions.OnError.STOP) {
        stopped = true;
      }
    }

    if (!request.options().partialOk()) {
      compensateIfAnyRejected(request, results);
    }

    ApiResponse response = ApiResponse.of(request.requestId(), results);
    idempotencyCache.put(request.requestId(), response);
    return response;
  }

  /**
   * All-or-nothing (task 8.5): when any item of a STAGE_ORDERS batch rejected, cancel the orders
   * that were staged and re-mark their results REJECTED, so the batch leaves no partial state
   * behind. The cancels publish OrderCanceled so subscription streams stay truthful.
   */
  private void compensateIfAnyRejected(ApiRequest request, List<ItemResult> results) {
    boolean anyRejected = results.stream().anyMatch(r -> r.status() != ItemResult.Status.ACCEPTED);
    if (!anyRejected) {
      return;
    }
    for (int i = 0; i < results.size(); i++) {
      ItemResult result = results.get(i);
      if (result.status() == ItemResult.Status.ACCEPTED && result.refId() != null) {
        som.cancel(result.refId(), request.sessionId());
        publishOrderEvent("OrderCanceled", result.refId());
        results.set(
            i,
            ItemResult.rejected(
                "EMS-ORD-3003",
                "Voided: all-or-nothing batch failed; staged order "
                    + result.refId()
                    + " canceled."));
      }
    }
  }

  private ItemResult executeItem(ApiRequest request, ApiItem item) {
    return switch (request.operation()) {
      case STAGE_ORDERS ->
          item instanceof ApiItem.StageOrder stage
              ? stageOrder(request, stage)
              : itemMismatch(request, item);
      case AMEND_ORDERS ->
          item instanceof ApiItem.AmendOrder amend
              ? amendOrder(request, amend)
              : itemMismatch(request, item);
      case CANCEL_ORDERS ->
          item instanceof ApiItem.CancelOrder cancel
              ? cancelOrder(request, cancel)
              : itemMismatch(request, item);
      case MARK_READY ->
          item instanceof ApiItem.MarkReady ready
              ? markReady(request, ready)
              : itemMismatch(request, item);
      case ROUTE_ORDERS ->
          item instanceof ApiItem.RouteOrder route
              ? routeOrder(request, route)
              : itemMismatch(request, item);
      case CANCEL_ROUTES ->
          item instanceof ApiItem.CancelRoute cancel
              ? cancelRoute(cancel)
              : itemMismatch(request, item);
      case SUBSCRIBE ->
          item instanceof ApiItem.Subscribe sub
              ? subscribe(request, sub)
              : itemMismatch(request, item);
      case UNSUBSCRIBE ->
          item instanceof ApiItem.Unsubscribe unsub
              ? unsubscribe(unsub)
              : itemMismatch(request, item);
    };
  }

  private ItemResult stageOrder(ApiRequest request, ApiItem.StageOrder item) {
    StageResult result =
        som.stage(
            new OrderRequest(
                request.requestId() + ":" + item.clOrdId(),
                request.sessionId(),
                item.clOrdId(),
                item.figi(),
                item.side(),
                item.qty(),
                item.price(),
                item.account(),
                item.tif()));
    if (result instanceof StageResult.Rejected rejected) {
      return ItemResult.rejected(rejected.rejectCode(), rejected.message());
    }
    String orderId = ((StageResult.Accepted) result).order().orderId();
    publishOrderEvent("OrderStaged", orderId);
    return ItemResult.accepted(orderId);
  }

  private ItemResult amendOrder(ApiRequest request, ApiItem.AmendOrder item) {
    AmendResult result =
        som.amend(item.orderId(), new AmendFields(item.qty(), item.price()), request.sessionId());
    if (result instanceof AmendResult.Rejected rejected) {
      return ItemResult.rejected(rejected.rejectCode(), rejected.message());
    }
    publishOrderEvent("OrderAmended", item.orderId());
    return ItemResult.accepted(item.orderId());
  }

  private ItemResult cancelOrder(ApiRequest request, ApiItem.CancelOrder item) {
    CancelResult result = som.cancel(item.orderId(), request.sessionId());
    if (result instanceof CancelResult.Rejected rejected) {
      return ItemResult.rejected(rejected.rejectCode(), rejected.message());
    }
    publishOrderEvent("OrderCanceled", item.orderId());
    return ItemResult.accepted(item.orderId());
  }

  private ItemResult markReady(ApiRequest request, ApiItem.MarkReady item) {
    MarkReadyResult result = som.markReady(item.orderId(), request.sessionId());
    if (result instanceof MarkReadyResult.Rejected rejected) {
      return ItemResult.rejected(rejected.rejectCode(), rejected.message());
    }
    publishOrderEvent("OrderReady", item.orderId());
    return ItemResult.accepted(item.orderId());
  }

  private ItemResult routeOrder(ApiRequest request, ApiItem.RouteOrder item) {
    RouteResult result =
        routeManager.route(
            new RouteRequest(
                request.requestId() + ":" + item.orderId(),
                item.orderId(),
                item.venueMic(),
                item.qty(),
                item.price(),
                null));
    if (result instanceof RouteResult.Rejected rejected) {
      return ItemResult.rejected(rejected.rejectCode(), rejected.message());
    }
    String routeId = ((RouteResult.Routed) result).route().routeId();
    publishOrderEvent("OrderRouted", item.orderId());
    return ItemResult.accepted(routeId);
  }

  private ItemResult cancelRoute(ApiItem.CancelRoute item) {
    RouteEventResult result = routeManager.cancelRoute(item.routeId());
    if (result instanceof RouteEventResult.Rejected rejected) {
      return ItemResult.rejected(rejected.rejectCode(), rejected.message());
    }
    return ItemResult.accepted(item.routeId());
  }

  private ItemResult subscribe(ApiRequest request, ApiItem.Subscribe item) {
    String subscriptionId =
        subscriptions.subscribe(request.sessionId(), item.topic(), item.fromSeq(), eventSink);
    return ItemResult.accepted(subscriptionId);
  }

  private ItemResult unsubscribe(ApiItem.Unsubscribe item) {
    return subscriptions.unsubscribe(item.subscriptionId())
        ? ItemResult.accepted(item.subscriptionId())
        : ItemResult.rejected(
            "EMS-SES-1002", "Subscription " + item.subscriptionId() + " unknown.");
  }

  private void publishOrderEvent(String type, String orderId) {
    subscriptions.publish(TOPIC_ORDERS, type, orderId, type + ":" + orderId);
    subscriptions.publish("order." + orderId, type, orderId, type + ":" + orderId);
  }

  private static ItemResult itemMismatch(ApiRequest request, ApiItem item) {
    return ItemResult.rejected(
        "EMS-ORD-1001",
        "Item type "
            + item.getClass().getSimpleName()
            + " does not match operation "
            + request.operation()
            + ".");
  }

  private static ApiResponse reject(ApiRequest request, String code, String message) {
    List<ItemResult> results = new ArrayList<>(request.items().size());
    for (int i = 0; i < request.items().size(); i++) {
      results.add(ItemResult.rejected(code, message));
    }
    return ApiResponse.of(request.requestId(), results);
  }
}
