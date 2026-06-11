/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.basket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.crossasset.ems.aaa.InMemoryAaaEventLog;
import io.crossasset.ems.aaa.InMemoryAaaService;
import io.crossasset.ems.api.ApiEvent;
import io.crossasset.ems.api.ApiSurface;
import io.crossasset.ems.api.SubscriptionRegistry;
import io.crossasset.ems.api.blotter.BlotterPublisher;
import io.crossasset.ems.api.blotter.BlotterRouteManager;
import io.crossasset.ems.api.blotter.BlotterStagedOrderManager;
import io.crossasset.ems.bulk.BulkOrderImporter;
import io.crossasset.ems.bulk.UploadResult;
import io.crossasset.ems.oms.InMemoryRouteManager;
import io.crossasset.ems.oms.InMemoryStagedOrderManager;
import io.crossasset.ems.oms.Route;
import io.crossasset.ems.validator.ValidationResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Basket / program trading tests (task 18.3): CSV list-load builds a basket from accepted rows
 * only; wave routing slices each constituent's remaining qty (mark-ready on the way, skip-filled,
 * per-line outcomes); the rollup row on blotter.baskets aggregates qty/cum/% and re-publishes live
 * as constituent fills arrive.
 */
class BasketServiceTest {

  private static final String FIGI_A = "BBG000B9XRY4";
  private static final String FIGI_B = "BBG000BPH459";
  private static final long SESSION = 9L;

  private final ObjectMapper mapper = new ObjectMapper();
  private final SubscriptionRegistry registry = new SubscriptionRegistry();
  private BlotterStagedOrderManager som;
  private BlotterRouteManager routes;
  private BasketService baskets;
  private long apiSession;
  private int seq = 1;

  @BeforeEach
  void setUp() {
    BlotterPublisher publisher = new BlotterPublisher(registry, () -> 1_000L);
    som =
        new BlotterStagedOrderManager(
            new InMemoryStagedOrderManager(
                request -> new ValidationResult.Pass(request.requestId())),
            publisher);
    routes = new BlotterRouteManager(new InMemoryRouteManager(som), publisher);

    InMemoryAaaService aaa = new InMemoryAaaService(new InMemoryAaaEventLog());
    aaa.registerCredential("tok-b", "firm", "desk-1", "trader-b", java.util.Set.of());
    var outcome =
        aaa.logon(
            io.crossasset.ems.aaa.LogonCredentials.fresh(
                io.crossasset.ems.aaa.CredentialKind.TOKEN, "tok-b"));
    apiSession = ((io.crossasset.ems.aaa.LogonOutcome.Accepted) outcome).session().sessionId();
    ApiSurface api = new ApiSurface(aaa, som, routes, registry, (sid, subId, event) -> {});
    baskets = new BasketService(som, routes, new BulkOrderImporter(api), registry);
  }

  private BasketService.Basket csvBasket() {
    UploadResult upload =
        baskets.createFromCsv(
            "tech-rebalance",
            "up-" + seq,
            apiSession,
            seq++,
            """
            client_order_id,figi,side,qty,account
            CL-A,%s,BUY,1000,acc-1
            CL-B,%s,SELL,400,acc-1
            """
                .formatted(FIGI_A, FIGI_B));
    assertThat(upload.accepted()).isEqualTo(2);
    return baskets.list().get(baskets.list().size() - 1);
  }

  private JsonNode lastRollup() throws Exception {
    List<ApiEvent> events = registry.fetch(BasketService.TOPIC_BASKETS, 1, 1_000);
    assertThat(events).isNotEmpty();
    return mapper.readTree(events.get(events.size() - 1).payload());
  }

  @Test
  void createFromCsv_buildsBasketFromAcceptedRowsOnly() throws Exception {
    UploadResult upload =
        baskets.createFromCsv(
            "mixed",
            "up-x",
            apiSession,
            seq++,
            """
            client_order_id,figi,side,qty,account
            CL-1,%s,BUY,100,acc-1
            CL-2,%s,NOT_A_SIDE,200,acc-1
            """
                .formatted(FIGI_A, FIGI_B));
    assertThat(upload.accepted()).isEqualTo(1);
    assertThat(upload.rejected()).isEqualTo(1);

    BasketService.Basket basket = baskets.list().get(0);
    assertThat(basket.orderIds()).hasSize(1);
    JsonNode rollup = lastRollup();
    assertThat(rollup.get("name").asText()).isEqualTo("mixed");
    assertThat(rollup.get("orders").asInt()).isEqualTo(1);
    assertThat(rollup.get("qty").asLong()).isEqualTo(100);
    assertThat(rollup.get("waves").asInt()).isZero();
  }

  @Test
  void waveRoute_slicesRemainingQtyAcrossConstituents_markReadyOnTheWay() throws Exception {
    BasketService.Basket basket = csvBasket();

    BasketService.WaveResult wave1 = baskets.waveRoute(basket.basketId(), 2_500, "XNAS", SESSION);

    assertThat(wave1.wave()).isEqualTo(1);
    assertThat(wave1.lines()).allMatch(BasketService.WaveLine::ok);
    List<Route> routesA = routes.findRoutesForOrder(basket.orderIds().get(0));
    List<Route> routesB = routes.findRoutesForOrder(basket.orderIds().get(1));
    assertThat(routesA).hasSize(1);
    assertThat(routesA.get(0).fsmContext().routeQty()).isEqualTo(250); // 25% of 1000
    assertThat(routesB.get(0).fsmContext().routeQty()).isEqualTo(100); // 25% of 400
    assertThat(lastRollup().get("waves").asInt()).isEqualTo(1);

    // Wave 2 at 100% takes everything not yet committed to open routes: 750 and 300.
    BasketService.WaveResult wave2 = baskets.waveRoute(basket.basketId(), 10_000, "XNYS", SESSION);
    assertThat(wave2.wave()).isEqualTo(2);
    assertThat(routes.findRoutesForOrder(basket.orderIds().get(0)))
        .extracting(r -> r.fsmContext().routeQty())
        .containsExactlyInAnyOrder(250L, 750L);
    assertThat(routes.findRoutesForOrder(basket.orderIds().get(1)))
        .extracting(r -> r.fsmContext().routeQty())
        .containsExactlyInAnyOrder(100L, 300L);
  }

  @Test
  void rollup_republishesLiveAsConstituentFillsArrive() throws Exception {
    BasketService.Basket basket = csvBasket();
    baskets.waveRoute(basket.basketId(), 10_000, "XNAS", SESSION);
    String orderA = basket.orderIds().get(0);
    Route routeA = routes.findRoutesForOrder(orderA).get(0);
    routes.acknowledgeRoute(routeA.routeId());

    routes.partialFill(routeA.routeId(), 600, 100_0000L, "EXEC-1");

    JsonNode rollup = lastRollup();
    assertThat(rollup.get("cumQty").asLong()).isEqualTo(600);
    assertThat(rollup.get("qty").asLong()).isEqualTo(1_400);
    assertThat(rollup.get("pctFilledBp").asLong()).isEqualTo(600L * 10_000 / 1_400);

    routes.fullFill(routeA.routeId(), 400, 100_0000L, "EXEC-2");
    assertThat(lastRollup().get("filled").asInt()).isEqualTo(1);
  }

  @Test
  void waveRoute_skipsFilledConstituents() throws Exception {
    BasketService.Basket basket = csvBasket();
    baskets.waveRoute(basket.basketId(), 10_000, "XNAS", SESSION);
    String orderA = basket.orderIds().get(0);
    Route routeA = routes.findRoutesForOrder(orderA).get(0);
    routes.acknowledgeRoute(routeA.routeId());
    routes.fullFill(routeA.routeId(), 1_000, 100_0000L, "EXEC-1");

    BasketService.WaveResult wave2 = baskets.waveRoute(basket.basketId(), 5_000, "XNYS", SESSION);

    BasketService.WaveLine lineA =
        wave2.lines().stream().filter(l -> l.orderId().equals(orderA)).findFirst().orElseThrow();
    assertThat(lineA.ok()).isTrue();
    assertThat(lineA.detail()).contains("skipped");
    assertThat(routes.findRoutesForOrder(orderA)).as("no new route for the filled name").hasSize(1);
  }

  @Test
  void createFromOrders_rejectsUnknownIds_andWaveRejectsUnknownBasket() {
    assertThatThrownBy(() -> baskets.createFromOrders("bad", List.of("ORD-NOPE")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> baskets.waveRoute("BSK-404", 1_000, "XNAS", SESSION))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> baskets.waveRoute("BSK-404", 0, "XNAS", SESSION))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
