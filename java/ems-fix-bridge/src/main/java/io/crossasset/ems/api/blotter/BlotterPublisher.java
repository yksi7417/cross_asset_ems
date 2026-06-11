/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.blotter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.crossasset.ems.api.SubscriptionRegistry;
import io.crossasset.ems.oms.Route;
import io.crossasset.ems.oms.StagedOrder;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Blotter row publisher (task 18.1): turns authoritative OMS state into flat JSON row deltas on the
 * 8.4 subscription topics — {@code blotter.orders} / {@code blotter.routes} / {@code blotter.fills}
 * — keyed by orderId / routeId / execId, exactly what the Perspective desktop feeds into indexed
 * tables via {@code table.update()} (row deltas merge by key; never a full refresh).
 *
 * <p>Per the order-manager workflow note, the blotter is a projection: rows are re-read from the
 * managers <em>after</em> each successful mutation (the {@link BlotterStagedOrderManager} / {@link
 * BlotterRouteManager} decorators), so a row always reflects FSM-validated state, and a client
 * rebuilding from topic seq 1 converges on the same image.
 */
public final class BlotterPublisher {

  /** Order rows, keyed by orderId. */
  public static final String TOPIC_ORDERS = "blotter.orders";

  /** Route rows, keyed by routeId. */
  public static final String TOPIC_ROUTES = "blotter.routes";

  /** Fill rows, keyed by execId (append-only). */
  public static final String TOPIC_FILLS = "blotter.fills";

  private final ObjectMapper mapper = new ObjectMapper();
  private final SubscriptionRegistry subscriptions;
  private final LongSupplier nowMicros;

  public BlotterPublisher(SubscriptionRegistry subscriptions, LongSupplier nowMicros) {
    this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
    this.nowMicros = Objects.requireNonNull(nowMicros, "nowMicros");
  }

  /** Publish the order's current image as a row delta. */
  public void publishOrder(StagedOrder order) {
    ObjectNode row = mapper.createObjectNode();
    row.put("orderId", order.orderId());
    row.put("clOrdId", order.fsmContext().clOrdId());
    row.put("figi", order.fsmContext().instrumentId());
    row.put("side", order.fsmContext().side());
    row.put("qty", order.fsmContext().orderQty());
    if (order.fsmContext().price() != null) {
      row.put("px", order.fsmContext().price());
    }
    row.put("cumQty", order.fsmContext().cumQty());
    row.put("leavesQty", order.fsmContext().leavesQty());
    row.put("account", order.fsmContext().account());
    row.put("tif", order.fsmContext().tif());
    row.put("state", order.fsmState().name());
    row.put("subState", order.subState().name());
    row.put("version", order.fsmContext().orderVersion());
    row.put("ts", order.stagedAtMicros());
    subscriptions.publish(TOPIC_ORDERS, "OrderRow", order.orderId(), row.toString());
  }

  /** Publish the route's current image as a row delta. */
  public void publishRoute(Route route) {
    ObjectNode row = mapper.createObjectNode();
    row.put("routeId", route.routeId());
    row.put("orderId", route.orderId());
    row.put("clOrdId", route.fsmContext().clOrdId());
    row.put("venueMic", route.fsmContext().venueMic());
    row.put("figi", route.fsmContext().instrumentId());
    row.put("side", route.fsmContext().side());
    row.put("qty", route.fsmContext().routeQty());
    if (route.fsmContext().price() != null) {
      row.put("px", route.fsmContext().price());
    }
    row.put("cumQty", route.fsmContext().cumQty());
    row.put("leavesQty", route.fsmContext().leavesQty());
    row.put("state", route.fsmState().name());
    row.put("ts", route.createdAtMicros());
    subscriptions.publish(TOPIC_ROUTES, "RouteRow", route.routeId(), row.toString());
  }

  /** Publish one execution as an append-only fill row. */
  public void publishFill(Route route, String execId, long lastQty, long lastPx) {
    ObjectNode row = mapper.createObjectNode();
    row.put("execId", execId);
    row.put("routeId", route.routeId());
    row.put("orderId", route.orderId());
    row.put("venueMic", route.fsmContext().venueMic());
    row.put("figi", route.fsmContext().instrumentId());
    row.put("side", route.fsmContext().side());
    row.put("lastQty", lastQty);
    row.put("lastPx", lastPx);
    row.put("ts", nowMicros.getAsLong());
    subscriptions.publish(TOPIC_FILLS, "FillRow", execId, row.toString());
  }
}
