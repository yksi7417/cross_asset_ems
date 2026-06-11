/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.rest;

import io.crossasset.ems.aaa.CredentialKind;
import io.crossasset.ems.aaa.InMemoryAaaEventLog;
import io.crossasset.ems.aaa.InMemoryAaaService;
import io.crossasset.ems.aaa.LogonCredentials;
import io.crossasset.ems.aaa.LogonOutcome;
import io.crossasset.ems.api.ApiSurface;
import io.crossasset.ems.api.SubscriptionRegistry;
import io.crossasset.ems.api.basket.BasketService;
import io.crossasset.ems.api.blotter.BlotterPublisher;
import io.crossasset.ems.api.blotter.BlotterRouteManager;
import io.crossasset.ems.api.blotter.BlotterStagedOrderManager;
import io.crossasset.ems.api.control.KillSwitchOrderGuard;
import io.crossasset.ems.api.control.KillSwitchRouteGuard;
import io.crossasset.ems.api.control.KillSwitchService;
import io.crossasset.ems.api.control.KillSwitchState;
import io.crossasset.ems.api.md.DeskWatchlist;
import io.crossasset.ems.api.md.MarketDataTopicBridge;
import io.crossasset.ems.bulk.BulkOrderImporter;
import io.crossasset.ems.instrument.AssetClass;
import io.crossasset.ems.instrument.CurrencyCode;
import io.crossasset.ems.instrument.Fungibility;
import io.crossasset.ems.instrument.InMemorySecurityMasterService;
import io.crossasset.ems.instrument.InstrumentCore;
import io.crossasset.ems.instrument.InstrumentType;
import io.crossasset.ems.instrument.InstrumentVersioned;
import io.crossasset.ems.instrument.LifecycleStatus;
import io.crossasset.ems.instrument.SecurityMasterEvent;
import io.crossasset.ems.instrument.SecurityMasterSnapshot;
import io.crossasset.ems.instrument.SettlementConvention;
import io.crossasset.ems.md.MdField;
import io.crossasset.ems.md.SimulatedFeed;
import io.crossasset.ems.oms.InMemoryRouteManager;
import io.crossasset.ems.oms.InMemoryStagedOrderManager;
import io.crossasset.ems.oms.OrderRequest;
import io.crossasset.ems.oms.RouteRequest;
import io.crossasset.ems.oms.RouteResult;
import io.crossasset.ems.oms.StageResult;
import io.crossasset.ems.pretrade.pnl.PnlService;
import io.crossasset.ems.pretrade.position.PositionService;
import io.crossasset.ems.pretrade.pricing.PricingService;
import io.crossasset.ems.validator.LayeredValidatorPipeline;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Demo/dev edge for the trader desktop (task 18.1): wires the real stack — AAA-backed validator,
 * blotter-decorated OMS, API surface, REST edge (orders/actions), WebSocket stream
 * (blotter/market-data topics), simulated market-data feed — and runs a scripted trading session so
 * the Perspective blotter streams live rows immediately.
 *
 * <pre>
 *   ./gradlew :ems-fix-bridge:runTraderEdge          # REST :8484, WS :8485
 *   cd ui/trader-desktop && npm install && npm run dev   # desktop on :5173, logon token "trader-token"
 * </pre>
 *
 * Not a production deployment — the production path binds the same components over the cluster
 * transport per arch-deployment.
 */
public final class TraderDesktopEdgeMain {

  private record Inst(String figi, String name, long basePx) {}

  private static final List<Inst> INSTRUMENTS =
      List.of(
          new Inst("BBG000B9XRY4", "Apple Inc", 182_4500L),
          new Inst("BBG000BPH459", "Microsoft Corp", 415_1200L),
          new Inst("BBG000BMHYD1", "JPMorgan Chase", 198_3300L),
          new Inst("BBG000BLNNH6", "IBM Corp", 168_7700L));

  private static final List<String> VENUES = List.of("XNAS", "XNYS", "ARCX");

  private TraderDesktopEdgeMain() {}

  public static void main(String[] args) throws Exception {
    int restPort = args.length > 0 ? Integer.parseInt(args[0]) : 8484;
    int wsPort = args.length > 1 ? Integer.parseInt(args[1]) : 8485;

    // ── The real stack ──────────────────────────────────────────────────────────
    InMemoryAaaService aaa = new InMemoryAaaService(new InMemoryAaaEventLog());
    aaa.registerCredential(
        "trader-token", "firm-demo", "desk-1", "trader-1", Set.of("#kill-switch"));
    aaa.registerCredential("demo-bot", "firm-demo", "desk-1", "demo-bot", Set.of());

    InMemorySecurityMasterService secMaster = new InMemorySecurityMasterService();
    SecurityMasterSnapshot snapshot = SecurityMasterSnapshot.EMPTY;
    long version = 1;
    for (Inst inst : INSTRUMENTS) {
      snapshot =
          snapshot.apply(
              new SecurityMasterEvent.InstrumentCreated(
                  new InstrumentVersioned(equity(inst.figi(), inst.name()), null), version++));
    }
    secMaster.publish(snapshot);

    SubscriptionRegistry subscriptions = new SubscriptionRegistry();
    BlotterPublisher blotter =
        new BlotterPublisher(subscriptions, () -> System.currentTimeMillis() * 1_000);
    LayeredValidatorPipeline pipeline = new LayeredValidatorPipeline(aaa, secMaster, null);
    BlotterStagedOrderManager blotterSom =
        new BlotterStagedOrderManager(new InMemoryStagedOrderManager(pipeline), blotter);
    BlotterRouteManager blotterRoutes =
        new BlotterRouteManager(new InMemoryRouteManager(blotterSom), blotter);
    // Kill-switch guards are outermost (18.4): every surface constructs against them.
    KillSwitchState killState = new KillSwitchState();
    KillSwitchOrderGuard som = new KillSwitchOrderGuard(blotterSom, killState, aaa);
    KillSwitchRouteGuard routes = new KillSwitchRouteGuard(blotterRoutes, killState, aaa, som);
    KillSwitchService killSwitch =
        new KillSwitchService(
            aaa, som, routes, killState, subscriptions, System::currentTimeMillis);

    ApiSurface api =
        new ApiSurface(aaa, som, routes, subscriptions, (sid, subId, event) -> {}, pipeline);
    BasketService baskets =
        new BasketService(som, routes, new BulkOrderImporter(api), subscriptions);
    RestHttpServer rest =
        new RestHttpServer(
            new RestEdgeBinding(aaa, api, subscriptions, secMaster, baskets, killSwitch), restPort);
    rest.start();
    WsEventStreamServer ws = new WsEventStreamServer(aaa, subscriptions, wsPort);
    ws.start();

    // ── Simulated market data over the 18.12 SPI → md topics ───────────────────
    SimulatedFeed feed = new SimulatedFeed("sim");
    MarketDataTopicBridge mdBridge = new MarketDataTopicBridge(subscriptions);
    mdBridge.attachHealth(feed);
    // Desk watchlist (18.14): seeding attaches each symbol to the bridge.
    DeskWatchlist watchlist =
        new DeskWatchlist(
            subscriptions,
            mdBridge,
            feed,
            Set.of(MdField.BID, MdField.ASK, MdField.LAST, MdField.VOLUME));
    for (Inst inst : INSTRUMENTS) {
      watchlist.add("desk-1", inst.figi());
    }
    feed.start();

    // ── Intraday P&L (18.7): fills feed positions, md ticks feed marks ─────────
    PositionService positionService = new PositionService();
    PricingService pricingService = new PricingService();
    PnlService pnlService = new PnlService(positionService, pricingService, figi -> "USD", "USD");
    com.fasterxml.jackson.databind.ObjectMapper pnlMapper =
        new com.fasterxml.jackson.databind.ObjectMapper();
    Thread.ofVirtual()
        .name("pnl-publisher")
        .start(
            () -> {
              var policy = PricingService.FallbackPolicy.conservative(10_000L, 60_000L);
              while (true) {
                try {
                  Thread.sleep(2_000);
                  var report =
                      pnlService.snapshot(
                          List.of("ACC-DEMO", "ACC-PROG", "ACC-UI"),
                          policy,
                          System.currentTimeMillis());
                  for (var row : report.rows()) {
                    var node = pnlMapper.createObjectNode();
                    node.put("key", row.account() + "|" + row.figi());
                    node.put("account", row.account());
                    node.put("figi", row.figi());
                    node.put("ccy", row.currency());
                    node.put("netQty", row.netQty());
                    node.put("avgCost", row.avgCost());
                    if (row.markPx() != null) {
                      node.put("markPx", row.markPx());
                    }
                    node.put("markSource", row.markSource());
                    node.put("realized", row.realizedLocal());
                    if (row.unrealizedLocal() != null) {
                      node.put("unrealized", row.unrealizedLocal());
                    }
                    subscriptions.publish(
                        "blotter.pnl", "PnlRow", node.get("key").asText(), node.toString());
                  }
                  var total = pnlMapper.createObjectNode();
                  total.put("key", "TOTAL");
                  total.put("account", "TOTAL (" + report.baseCurrency() + ")");
                  total.put("figi", "");
                  total.put("ccy", report.baseCurrency());
                  total.put("realized", report.totalRealizedBase());
                  total.put("unrealized", report.totalUnrealizedBase());
                  total.put("markSource", report.unmarked() + " unmarked");
                  subscriptions.publish("blotter.pnl", "PnlRow", "TOTAL", total.toString());
                } catch (InterruptedException e) {
                  return;
                }
              }
            });

    System.out.println("REST edge      : http://localhost:" + rest.port() + "/api/v1");
    System.out.println("WS stream      : ws://localhost:" + ws.port() + "/ws/events");
    System.out.println("Desktop logon  : token \"trader-token\"");

    runDemoScript(aaa, som, routes, feed, positionService, pricingService);
  }

  /** A scripted trading session: staged orders, routes, dripped fills, ticking quotes. */
  private static void runDemoScript(
      InMemoryAaaService aaa,
      io.crossasset.ems.oms.StagedOrderManager som,
      io.crossasset.ems.oms.RouteManager routes,
      SimulatedFeed feed,
      PositionService positionService,
      PricingService pricingService)
      throws InterruptedException {
    LogonOutcome logon = aaa.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "demo-bot"));
    long session = ((LogonOutcome.Accepted) logon).session().sessionId();
    Random random = new Random();
    long[] lastPx = INSTRUMENTS.stream().mapToLong(Inst::basePx).toArray();
    long volume = 0;
    int orderSeq = 0;

    while (true) {
      // Quotes tick every cycle (random walk, ±5 bps).
      for (int i = 0; i < INSTRUMENTS.size(); i++) {
        Inst inst = INSTRUMENTS.get(i);
        lastPx[i] += Math.round(lastPx[i] * (random.nextDouble() - 0.5) * 0.001);
        long spread = Math.max(100, lastPx[i] / 2_000);
        volume += random.nextInt(900) + 100;
        feed.emit(
            inst.figi(),
            Map.of(
                MdField.BID,
                lastPx[i] - spread,
                MdField.ASK,
                lastPx[i] + spread,
                MdField.LAST,
                lastPx[i],
                MdField.VOLUME,
                volume),
            System.currentTimeMillis());
        pricingService.recordLive(inst.figi(), lastPx[i], System.currentTimeMillis());
      }

      // Every few cycles: a new order through stage → ready → route → ack → fills.
      if (orderSeq < 999 && random.nextInt(4) == 0) {
        orderSeq++;
        int i = random.nextInt(INSTRUMENTS.size());
        Inst inst = INSTRUMENTS.get(i);
        long qty = (random.nextInt(20) + 1) * 100L;
        int side = random.nextInt(2) + 1;
        StageResult staged =
            som.stage(
                new OrderRequest(
                    "demo-" + orderSeq,
                    session,
                    "CL-" + orderSeq,
                    inst.figi(),
                    side,
                    qty,
                    lastPx[i],
                    "ACC-DEMO",
                    0));
        if (staged instanceof StageResult.Accepted accepted) {
          String orderId = accepted.order().orderId();
          som.markReady(orderId, session);
          RouteResult routed =
              routes.route(
                  new RouteRequest(
                      "RCL-" + orderSeq,
                      orderId,
                      VENUES.get(random.nextInt(VENUES.size())),
                      qty,
                      lastPx[i],
                      null));
          if (routed instanceof RouteResult.Routed r) {
            String routeId = r.route().routeId();
            routes.acknowledgeRoute(routeId);
            long remaining = qty;
            int execSeq = 0;
            while (remaining > 0 && random.nextInt(3) > 0) {
              long fillQty = Math.min(remaining, (random.nextInt(5) + 1) * 100L);
              remaining -= fillQty;
              String execId = "EXEC-" + orderSeq + "-" + ++execSeq;
              if (remaining == 0) {
                routes.fullFill(routeId, fillQty, lastPx[i], execId);
              } else {
                routes.partialFill(routeId, fillQty, lastPx[i], execId);
              }
              positionService.applyFill(
                  new PositionService.Fill(
                      execId,
                      "ACC-DEMO",
                      inst.figi(),
                      side == 1 ? 1 : 2,
                      fillQty,
                      lastPx[i],
                      orderSeq * 1_000L + execSeq));
              Thread.sleep(150);
            }
          }
        }
      }
      Thread.sleep(400);
    }
  }

  private static InstrumentCore equity(String figi, String name) {
    return new InstrumentCore(
        figi,
        "IID-" + figi,
        null,
        null,
        AssetClass.EQUITY,
        InstrumentType.COMMON_STOCK,
        name,
        name,
        null,
        CurrencyCode.USD,
        "US",
        null,
        Fungibility.FUNGIBLE,
        SettlementConvention.T_PLUS_2,
        0,
        LifecycleStatus.ACTIVE,
        1_000_000L,
        Long.MAX_VALUE,
        1L,
        null,
        1_000_000L,
        1_000_000L);
  }
}
