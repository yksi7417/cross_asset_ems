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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
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

  // Execution accumulators for average price (18.22): Σqty / Σ(qty×px) per order and per route.
  // Recorded by BlotterRouteManager BEFORE the fill is delegated (the order row publishes from
  // inside the delegate call, which would otherwise see a one-fill-stale average) and rolled back
  // if the Route FSM rejects the event.
  private final Map<String, long[]> orderExec = new ConcurrentHashMap<>();
  private final Map<String, long[]> routeExec = new ConcurrentHashMap<>();

  public BlotterPublisher(SubscriptionRegistry subscriptions, LongSupplier nowMicros) {
    this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
    this.nowMicros = Objects.requireNonNull(nowMicros, "nowMicros");
  }

  /** Record an execution into the order/route average-price accumulators. */
  public void recordExecution(String orderId, String routeId, long lastQty, long lastPx) {
    accumulate(orderExec, orderId, lastQty, lastPx);
    accumulate(routeExec, routeId, lastQty, lastPx);
  }

  /** Roll a recorded execution back (the Route FSM rejected the fill event). */
  public void unrecordExecution(String orderId, String routeId, long lastQty, long lastPx) {
    accumulate(orderExec, orderId, -lastQty, lastPx);
    accumulate(routeExec, routeId, -lastQty, lastPx);
  }

  private static void accumulate(Map<String, long[]> map, String key, long qty, long px) {
    map.compute(
        key,
        (k, acc) -> {
          // Copy-on-write (L4-6): return a FRESH array rather than mutating `acc` in place, so a
          // concurrent averagePx() holding a previously-published reference always reads a
          // consistent (cumQty, cumNotional) pair — never a torn read (qty from before an update,
          // notional from after). compute() is atomic per key, so the accumulation stays correct.
          long cumQty = acc == null ? 0 : acc[0];
          long cumNotional = acc == null ? 0 : acc[1];
          return new long[] {cumQty + qty, cumNotional + qty * px};
        });
  }

  private static Long averagePx(Map<String, long[]> map, String key) {
    long[] acc = map.get(key);
    return acc == null || acc[0] <= 0 ? null : Math.round((double) acc[1] / acc[0]);
  }

  /** FIX tag-59 TIF labels (trader-readable blotter column, 18.22). */
  private static String tifLabel(int tif) {
    return switch (tif) {
      case 0 -> "DAY";
      case 1 -> "GTC";
      case 2 -> "OPG";
      case 3 -> "IOC";
      case 4 -> "FOK";
      case 6 -> "GTD";
      case 7 -> "CLS";
      default -> String.valueOf(tif);
    };
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
    row.put("tif", tifLabel(order.fsmContext().tif()));
    row.put("ordType", order.fsmContext().price() != null ? "LMT" : "MKT");
    Long orderAvg = averagePx(orderExec, order.orderId());
    if (orderAvg != null) {
      row.put("avgPx", orderAvg.longValue());
    }
    row.put("state", order.fsmState().name());
    row.put("subState", order.subState().name());
    row.put("version", order.fsmContext().orderVersion());
    row.put("ts", order.stagedAtMicros());
    row.put("asOf", nowMicros.getAsLong()); // event time — orders the audit timeline (18.25)
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
    Long routeAvg = averagePx(routeExec, route.routeId());
    if (routeAvg != null) {
      row.put("avgPx", routeAvg.longValue());
    }
    row.put("state", route.fsmState().name());
    row.put("ts", route.createdAtMicros());
    row.put("asOf", nowMicros.getAsLong()); // event time — orders the audit timeline (18.25)
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
    long now = nowMicros.getAsLong();
    row.put("ts", now);
    row.put("asOf", now);
    subscriptions.publish(TOPIC_FILLS, "FillRow", execId, row.toString());
  }
}
