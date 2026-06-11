/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.basket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.crossasset.ems.api.SubscriptionRegistry;
import io.crossasset.ems.api.blotter.BlotterPublisher;
import io.crossasset.ems.bulk.BulkOrderImporter;
import io.crossasset.ems.bulk.UploadResult;
import io.crossasset.ems.oms.MarkReadyResult;
import io.crossasset.ems.oms.OrderSubState;
import io.crossasset.ems.oms.RouteManager;
import io.crossasset.ems.oms.RouteRequest;
import io.crossasset.ems.oms.RouteResult;
import io.crossasset.ems.oms.StagedOrder;
import io.crossasset.ems.oms.StagedOrderManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Basket / program trading (task 18.3): a basket is a named list of <em>independent</em> staged
 * orders traded as a campaign — list-loaded via the 8.6 CSV importer, routed in waves, monitored as
 * an aggregate. Deliberately distinct from structurally-linked multi-leg packages (7.4, atomic
 * lifecycle) and block aggregation (7.5, children frozen under one parent): basket constituents
 * keep their own FSMs and can be worked individually.
 *
 * <p>Wave routing slices a fraction of every constituent's <em>remaining</em> qty to a venue in one
 * action (wave 1: 25% to XNAS; wave 2: 50% of what's left; …). Constituents still NEW are
 * mark-ready'd on the way (routing a wave is explicit trader intent); slices route through the same
 * {@link RouteManager} as single-order tickets, so blotter rows, fills, and audit are identical.
 *
 * <p>Aggregate monitoring: a flat rollup row (orders, qty, cum, %, waves) publishes on {@code
 * blotter.baskets} after every basket action <em>and</em> on every constituent order-row event (the
 * service subscribes to {@code blotter.orders}), so the desktop's basket grid ticks as fills
 * arrive.
 */
public final class BasketService {

  /** Basket rollup rows, keyed by basketId. */
  public static final String TOPIC_BASKETS = "blotter.baskets";

  /** One basket: membership + wave count. Constituent state lives in the SOM, never here. */
  public record Basket(String basketId, String name, List<String> orderIds, int waves) {}

  /** Per-constituent outcome of one wave. */
  public record WaveLine(String orderId, boolean ok, String detail) {}

  /** Result of one wave action. */
  public record WaveResult(String basketId, int wave, List<WaveLine> lines) {}

  private final StagedOrderManager som;
  private final RouteManager routes;
  private final BulkOrderImporter importer;
  private final SubscriptionRegistry subscriptions;
  private final ObjectMapper mapper = new ObjectMapper();
  private final Map<String, Basket> baskets = new LinkedHashMap<>();
  private final AtomicLong basketSeq = new AtomicLong(1);

  public BasketService(
      StagedOrderManager som,
      RouteManager routes,
      BulkOrderImporter importer,
      SubscriptionRegistry subscriptions) {
    this.som = Objects.requireNonNull(som, "som");
    this.routes = Objects.requireNonNull(routes, "routes");
    this.importer = Objects.requireNonNull(importer, "importer");
    this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
    // Live aggregate monitoring: any constituent order-row delta re-publishes its baskets' rollups.
    subscriptions.subscribe(0L, BlotterPublisher.TOPIC_ORDERS, Long.MAX_VALUE, this::onOrderEvent);
  }

  /** Load a basket from CSV via the 8.6 importer; only accepted rows join the basket. */
  public synchronized UploadResult createFromCsv(
      String name, String uploadId, long sessionId, long sessionSeq, String csvText) {
    UploadResult result = importer.importCsv(uploadId, sessionId, sessionSeq, csvText);
    List<String> orderIds = new ArrayList<>();
    for (UploadResult.RowResult row : result.rows()) {
      if (row.ok() && row.refId() != null) {
        orderIds.add(row.refId());
      }
    }
    if (!orderIds.isEmpty()) {
      register(name, orderIds);
    }
    return result;
  }

  /** Build a basket from already-staged orders. Throws on an unknown order ID. */
  public synchronized Basket createFromOrders(String name, List<String> orderIds) {
    for (String orderId : orderIds) {
      if (som.findOrder(orderId).isEmpty()) {
        throw new IllegalArgumentException("Unknown order: " + orderId);
      }
    }
    return register(name, orderIds);
  }

  /** All baskets, in creation order. */
  public synchronized List<Basket> list() {
    return List.copyOf(baskets.values());
  }

  public synchronized Optional<Basket> find(String basketId) {
    return Optional.ofNullable(baskets.get(basketId));
  }

  /**
   * Route a wave: {@code fractionBp} basis points (2500 = 25%) of each constituent's remaining
   * <em>unrouted</em> qty (order leaves minus open route commitments) to {@code venueMic}. NEW
   * constituents are mark-ready'd first; zero slices (filled/terminal constituents) are skipped.
   * Returns per-constituent outcomes.
   */
  public synchronized WaveResult waveRoute(
      String basketId, int fractionBp, String venueMic, long sessionId) {
    Basket basket = baskets.get(basketId);
    if (basket == null) {
      throw new IllegalArgumentException("Unknown basket: " + basketId);
    }
    if (fractionBp <= 0 || fractionBp > 10_000) {
      throw new IllegalArgumentException("fractionBp must be in (0, 10000]: " + fractionBp);
    }
    int wave = basket.waves() + 1;
    List<WaveLine> lines = new ArrayList<>();
    for (String orderId : basket.orderIds()) {
      lines.add(waveLine(orderId, wave, fractionBp, venueMic, sessionId));
    }
    baskets.put(basketId, new Basket(basketId, basket.name(), basket.orderIds(), wave));
    publishRollup(baskets.get(basketId));
    return new WaveResult(basketId, wave, lines);
  }

  private WaveLine waveLine(
      String orderId, int wave, int fractionBp, String venueMic, long sessionId) {
    Optional<StagedOrder> orderOpt = som.findOrder(orderId);
    if (orderOpt.isEmpty()) {
      return new WaveLine(orderId, false, "unknown order");
    }
    StagedOrder order = orderOpt.get();
    if (order.isTerminal()) {
      return new WaveLine(orderId, true, "skipped: terminal (" + order.fsmState() + ")");
    }
    if (order.subState() == OrderSubState.NEW || order.subState() == OrderSubState.STAGED) {
      MarkReadyResult ready = som.markReady(orderId, sessionId);
      if (ready instanceof MarkReadyResult.Rejected rejected) {
        return new WaveLine(orderId, false, rejected.rejectCode() + ": " + rejected.message());
      }
      order = som.findOrder(orderId).orElse(order);
    }
    // Available-to-route = order leaves minus qty already committed to open routes — a wave
    // never over-commits the order (the router enforces EMS-RTE-4003; we slice within it).
    long committed = 0;
    for (var route : routes.findRoutesForOrder(orderId)) {
      if (!route.isTerminal()) {
        committed += route.fsmContext().leavesQty();
      }
    }
    long available = order.fsmContext().leavesQty() - committed;
    long slice = available * fractionBp / 10_000;
    if (slice <= 0) {
      return new WaveLine(orderId, true, "skipped: nothing to route");
    }
    RouteResult routed =
        routes.route(
            new RouteRequest("W" + wave + "-" + orderId, orderId, venueMic, slice, null, null));
    if (routed instanceof RouteResult.Rejected rejected) {
      return new WaveLine(orderId, false, rejected.rejectCode() + ": " + rejected.message());
    }
    return new WaveLine(
        orderId,
        true,
        "routed " + slice + " via " + ((RouteResult.Routed) routed).route().routeId());
  }

  // ── Rollup ───────────────────────────────────────────────────────────────────

  private Basket register(String name, List<String> orderIds) {
    String basketId = "BSK-" + basketSeq.getAndIncrement();
    Basket basket = new Basket(basketId, name, List.copyOf(orderIds), 0);
    baskets.put(basketId, basket);
    publishRollup(basket);
    return basket;
  }

  private void onOrderEvent(
      long sessionId, String subscriptionId, io.crossasset.ems.api.ApiEvent event) {
    synchronized (this) {
      for (Basket basket : baskets.values()) {
        if (basket.orderIds().contains(event.refId())) {
          publishRollup(basket);
        }
      }
    }
  }

  /** Flat rollup row for the desktop's basket grid (keyed by basketId). */
  private void publishRollup(Basket basket) {
    long totalQty = 0;
    long cumQty = 0;
    long leavesQty = 0;
    int filled = 0;
    int terminal = 0;
    for (String orderId : basket.orderIds()) {
      Optional<StagedOrder> orderOpt = som.findOrder(orderId);
      if (orderOpt.isEmpty()) {
        continue;
      }
      StagedOrder order = orderOpt.get();
      totalQty += order.fsmContext().orderQty();
      cumQty += order.fsmContext().cumQty();
      leavesQty += order.fsmContext().leavesQty();
      if ("FILLED".equals(order.fsmState().name())) {
        filled++;
      } else if (order.isTerminal()) {
        terminal++;
      }
    }
    ObjectNode row = mapper.createObjectNode();
    row.put("basketId", basket.basketId());
    row.put("name", basket.name());
    row.put("orders", basket.orderIds().size());
    row.put("qty", totalQty);
    row.put("cumQty", cumQty);
    row.put("leavesQty", leavesQty);
    row.put("pctFilledBp", totalQty == 0 ? 0 : cumQty * 10_000 / totalQty);
    row.put("filled", filled);
    row.put("otherTerminal", terminal);
    row.put("waves", basket.waves());
    subscriptions.publish(TOPIC_BASKETS, "BasketRow", basket.basketId(), row.toString());
  }
}
