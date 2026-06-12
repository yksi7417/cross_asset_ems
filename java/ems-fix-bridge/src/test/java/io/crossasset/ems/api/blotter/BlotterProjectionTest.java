/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.blotter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.crossasset.ems.api.ApiEvent;
import io.crossasset.ems.api.SubscriptionRegistry;
import io.crossasset.ems.oms.InMemoryRouteManager;
import io.crossasset.ems.oms.InMemoryStagedOrderManager;
import io.crossasset.ems.oms.OrderRequest;
import io.crossasset.ems.oms.RouteRequest;
import io.crossasset.ems.oms.RouteResult;
import io.crossasset.ems.oms.StageResult;
import io.crossasset.ems.validator.ValidationResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Blotter projection tests (task 18.1): the decorated managers publish keyed row deltas on
 * blotter.orders / blotter.routes / blotter.fills for the full order-manager workflow — stage →
 * ready → route → ack → fills — including the venue-driven propagation path (a route fill updates
 * the parent order row through the decorated som with no extra wiring).
 */
class BlotterProjectionTest {

  private static final String FIGI = "BBG000BLNNH6";
  private static final long SESSION = 7L;

  private final ObjectMapper mapper = new ObjectMapper();
  private final SubscriptionRegistry registry = new SubscriptionRegistry();
  private final BlotterPublisher publisher = new BlotterPublisher(registry, () -> 5_000_000L);
  private final BlotterStagedOrderManager som =
      new BlotterStagedOrderManager(
          new InMemoryStagedOrderManager(request -> new ValidationResult.Pass(request.requestId())),
          publisher);
  private final BlotterRouteManager routes =
      new BlotterRouteManager(new InMemoryRouteManager(som), publisher);

  private String stageReadyOrder() {
    StageResult staged =
        som.stage(
            new OrderRequest("req-1", SESSION, "CL-1", FIGI, 1, 1_000L, 100_2500L, "ACC-1", 0));
    String orderId = ((StageResult.Accepted) staged).order().orderId();
    som.markReady(orderId, SESSION);
    return orderId;
  }

  private JsonNode lastRow(String topic) throws Exception {
    List<ApiEvent> events = registry.fetch(topic, 1, 1_000);
    assertThat(events).isNotEmpty();
    return mapper.readTree(events.get(events.size() - 1).payload());
  }

  @Test
  void stage_publishesOrderRowWithFullImage() throws Exception {
    String orderId = stageReadyOrder();

    List<ApiEvent> events = registry.fetch(BlotterPublisher.TOPIC_ORDERS, 1, 10);
    assertThat(events).extracting(ApiEvent::type).containsOnly("OrderRow");
    assertThat(events).extracting(ApiEvent::refId).containsOnly(orderId);
    JsonNode row = lastRow(BlotterPublisher.TOPIC_ORDERS);
    assertThat(row.get("orderId").asText()).isEqualTo(orderId);
    assertThat(row.get("clOrdId").asText()).isEqualTo("CL-1");
    assertThat(row.get("figi").asText()).isEqualTo(FIGI);
    assertThat(row.get("side").asInt()).isEqualTo(1);
    assertThat(row.get("qty").asLong()).isEqualTo(1_000L);
    assertThat(row.get("px").asLong()).isEqualTo(100_2500L);
    assertThat(row.get("account").asText()).isEqualTo("ACC-1");
    assertThat(row.get("state").asText()).isEqualTo("NEW");
    assertThat(row.get("subState").asText()).isEqualTo("READY");
  }

  @Test
  void route_publishesRouteRow_andOrderRowFlipsToRouting() throws Exception {
    String orderId = stageReadyOrder();
    RouteResult routed =
        routes.route(new RouteRequest("RCL-1", orderId, "XNAS", 600L, 100_2500L, null));
    String routeId = ((RouteResult.Routed) routed).route().routeId();

    JsonNode routeRow = lastRow(BlotterPublisher.TOPIC_ROUTES);
    assertThat(routeRow.get("routeId").asText()).isEqualTo(routeId);
    assertThat(routeRow.get("orderId").asText()).isEqualTo(orderId);
    assertThat(routeRow.get("venueMic").asText()).isEqualTo("XNAS");
    assertThat(routeRow.get("qty").asLong()).isEqualTo(600L);
    assertThat(routeRow.get("state").asText()).isEqualTo("SENT");

    JsonNode orderRow = lastRow(BlotterPublisher.TOPIC_ORDERS);
    assertThat(orderRow.get("subState").asText()).isEqualTo("ROUTING");
  }

  @Test
  void fills_publishRouteUpdate_fillRow_andParentOrderCumQty() throws Exception {
    String orderId = stageReadyOrder();
    String routeId =
        ((RouteResult.Routed)
                routes.route(new RouteRequest("RCL-1", orderId, "XNAS", 1_000L, null, null)))
            .route()
            .routeId();
    routes.acknowledgeRoute(routeId);

    routes.partialFill(routeId, 400L, 100_1000L, "EXEC-1");

    JsonNode fillRow = lastRow(BlotterPublisher.TOPIC_FILLS);
    assertThat(fillRow.get("execId").asText()).isEqualTo("EXEC-1");
    assertThat(fillRow.get("routeId").asText()).isEqualTo(routeId);
    assertThat(fillRow.get("orderId").asText()).isEqualTo(orderId);
    assertThat(fillRow.get("lastQty").asLong()).isEqualTo(400L);
    assertThat(fillRow.get("lastPx").asLong()).isEqualTo(100_1000L);
    assertThat(fillRow.get("ts").asLong()).isEqualTo(5_000_000L);

    JsonNode routeRow = lastRow(BlotterPublisher.TOPIC_ROUTES);
    assertThat(routeRow.get("cumQty").asLong()).isEqualTo(400L);
    assertThat(routeRow.get("leavesQty").asLong()).isEqualTo(600L);
    assertThat(routeRow.get("state").asText()).isEqualTo("PARTIALLY_FILLED");

    // Venue-driven propagation: the parent order row updated through the decorated som.
    JsonNode orderRow = lastRow(BlotterPublisher.TOPIC_ORDERS);
    assertThat(orderRow.get("cumQty").asLong()).isEqualTo(400L);
    assertThat(orderRow.get("state").asText()).isEqualTo("PARTIALLY_FILLED");

    routes.fullFill(routeId, 600L, 100_2000L, "EXEC-2");
    assertThat(lastRow(BlotterPublisher.TOPIC_ROUTES).get("state").asText()).isEqualTo("FILLED");
    assertThat(lastRow(BlotterPublisher.TOPIC_ORDERS).get("state").asText()).isEqualTo("FILLED");
    assertThat(registry.fetch(BlotterPublisher.TOPIC_FILLS, 1, 10)).hasSize(2);
  }

  @Test
  void orderRow_carriesTifLabel_ordType_andWeightedAveragePx() throws Exception {
    String orderId = stageReadyOrder(); // limit 100.25, tif 0
    JsonNode staged = lastRow(BlotterPublisher.TOPIC_ORDERS);
    assertThat(staged.get("tif").asText()).isEqualTo("DAY");
    assertThat(staged.get("ordType").asText()).isEqualTo("LMT");
    assertThat(staged.has("avgPx")).isFalse(); // nothing executed yet

    String routeId =
        ((RouteResult.Routed)
                routes.route(new RouteRequest("RCL-1", orderId, "XNAS", 1_000L, null, null)))
            .route()
            .routeId();
    routes.acknowledgeRoute(routeId);
    routes.partialFill(routeId, 400L, 100_0000L, "EXEC-1");
    routes.fullFill(routeId, 600L, 101_0000L, "EXEC-2");

    // WAP = (400×100.0000 + 600×101.0000) / 1000 = 100.6000 — INCLUDING the final fill on the
    // terminal row (the accumulator is recorded before the order row publishes).
    JsonNode orderRow = lastRow(BlotterPublisher.TOPIC_ORDERS);
    assertThat(orderRow.get("state").asText()).isEqualTo("FILLED");
    assertThat(orderRow.get("avgPx").asLong()).isEqualTo(100_6000L);
    JsonNode routeRow = lastRow(BlotterPublisher.TOPIC_ROUTES);
    assertThat(routeRow.get("avgPx").asLong()).isEqualTo(100_6000L);
  }

  @Test
  void marketOrder_publishesOrdTypeMkt() throws Exception {
    StageResult staged =
        som.stage(new OrderRequest("req-mkt", SESSION, "CL-MKT", FIGI, 1, 500L, null, "ACC-1", 3));
    assertThat(staged).isInstanceOf(StageResult.Accepted.class);
    JsonNode row = lastRow(BlotterPublisher.TOPIC_ORDERS);
    assertThat(row.get("ordType").asText()).isEqualTo("MKT");
    assertThat(row.get("tif").asText()).isEqualTo("IOC");
  }

  @Test
  void rejectedFill_rollsBackTheAverageAccumulator() throws Exception {
    String orderId = stageReadyOrder();
    String routeId =
        ((RouteResult.Routed)
                routes.route(new RouteRequest("RCL-1", orderId, "XNAS", 1_000L, null, null)))
            .route()
            .routeId();
    // No venue ack: a fill on a SENT route is rejected by the Route FSM (EMS-RTE-5002 class).
    routes.partialFill(routeId, 400L, 999_0000L, "EXEC-BAD");
    routes.acknowledgeRoute(routeId);
    routes.fullFill(routeId, 1_000L, 100_0000L, "EXEC-1");

    // The rejected execution must not pollute the average: WAP = 100.0000, not a blend.
    assertThat(lastRow(BlotterPublisher.TOPIC_ORDERS).get("avgPx").asLong())
        .isEqualTo(100_0000L);
  }

  @Test
  void cancel_publishesTerminalOrderRow() throws Exception {
    String orderId = stageReadyOrder();
    som.cancel(orderId, SESSION);

    JsonNode row = lastRow(BlotterPublisher.TOPIC_ORDERS);
    assertThat(row.get("orderId").asText()).isEqualTo(orderId);
    assertThat(row.get("state").asText()).isEqualTo("CANCELED");
  }

  @Test
  void rejectedMutation_publishesNothing() {
    long before = registry.fetch(BlotterPublisher.TOPIC_ORDERS, 1, 1_000).size();
    som.cancel("ORD-UNKNOWN", SESSION);
    routes.partialFill("RTE-UNKNOWN", 1L, 1L, "EXEC-X");

    assertThat(registry.fetch(BlotterPublisher.TOPIC_ORDERS, 1, 1_000)).hasSize((int) before);
    assertThat(registry.fetch(BlotterPublisher.TOPIC_FILLS, 1, 1_000)).isEmpty();
  }

  @Test
  void rowsAreKeyedDeltas_clientRebuildFromSeq1ConvergesOnLatestImage() throws Exception {
    String orderId = stageReadyOrder();
    String routeId =
        ((RouteResult.Routed)
                routes.route(new RouteRequest("RCL-1", orderId, "XNAS", 1_000L, null, null)))
            .route()
            .routeId();
    routes.acknowledgeRoute(routeId);
    routes.fullFill(routeId, 1_000L, 99_0000L, "EXEC-1");

    // Replay everything the way a fresh Perspective table does (merge by orderId key).
    List<ApiEvent> orderEvents = registry.fetch(BlotterPublisher.TOPIC_ORDERS, 1, 1_000);
    JsonNode finalImage = mapper.readTree(orderEvents.get(orderEvents.size() - 1).payload());
    assertThat(orderEvents.size()).isGreaterThanOrEqualTo(3); // staged, ready, routing, filled…
    assertThat(finalImage.get("state").asText()).isEqualTo("FILLED");
    assertThat(finalImage.get("cumQty").asLong()).isEqualTo(1_000L);
    assertThat(finalImage.get("leavesQty").asLong()).isEqualTo(0L);
  }
}
