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
import io.crossasset.ems.api.notify.DesktopSink;
import io.crossasset.ems.api.notify.NotificationService;
import io.crossasset.ems.bulk.BulkOrderImporter;
import io.crossasset.ems.instrument.InMemorySecurityMasterService;
import io.crossasset.ems.instrument.InstrumentVersioned;
import io.crossasset.ems.instrument.SecurityMasterEvent;
import io.crossasset.ems.instrument.SecurityMasterSnapshot;
import io.crossasset.ems.md.MdField;
import io.crossasset.ems.md.SimulatedFeed;
import io.crossasset.ems.observability.EmsOpenTelemetry;
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
import io.crossasset.ems.venue.esp.EspClickService;
import io.crossasset.ems.venue.esp.MockEspVenue;
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

  // Cross-asset demo universe (18.21): ≥2 instruments per supported asset class (1 US + 1
  // international), each carrying its own lot/venue conventions. Defined in DemoUniverse.
  private static final List<DemoUniverse.DemoInstrument> INSTRUMENTS = DemoUniverse.INSTRUMENTS;

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
    for (DemoUniverse.DemoInstrument inst : INSTRUMENTS) {
      snapshot =
          snapshot.apply(
              new SecurityMasterEvent.InstrumentCreated(
                  new InstrumentVersioned(inst.core(), null), version++));
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
    io.crossasset.ems.oms.RouteManager complianceRoutes = blotterRoutes;
    if ("1".equals(System.getenv("EMS_COMPLIANCE_GATE"))) {
      // List gate (10.4): lists are compliance-authored reference data. The demo edge seeds
      // the firm restricted list from EMS_RESTRICTED_FIGIS (comma-separated) so a blocked
      // route is one env var away; empty lists mean the check allows everything.
      io.crossasset.ems.pretrade.compliance.ComplianceListService complianceLists =
          new io.crossasset.ems.pretrade.compliance.ComplianceListService();
      String restrictedFigis = System.getenv("EMS_RESTRICTED_FIGIS");
      if (restrictedFigis != null && !restrictedFigis.isBlank()) {
        for (String figi : restrictedFigis.split(",")) {
          String trimmed = figi.trim();
          if (trimmed.isEmpty()) {
            continue;
          }
          complianceLists.add(
              io.crossasset.ems.pretrade.compliance.ComplianceListService.Kind.RESTRICTED,
              "firm-demo",
              trimmed,
              0L,
              null);
        }
      }
      complianceRoutes =
          new io.crossasset.ems.api.control.ComplianceRouteGuard(
              blotterRoutes,
              new io.crossasset.ems.pretrade.compliance.ComplianceGate(
                  java.util.List.of(
                      new io.crossasset.ems.pretrade.compliance.MachineGunCheck(
                          new io.crossasset.ems.pretrade.compliance.MachineGunCheck.Policy(
                              60_000L, 50, 1_000_000_000L, 20),
                          System::currentTimeMillis),
                      new io.crossasset.ems.pretrade.compliance.ListCheck(
                          complianceLists, System::currentTimeMillis))),
              som,
              "firm-demo",
              "desk-1");
    }
    KillSwitchRouteGuard routes = new KillSwitchRouteGuard(complianceRoutes, killState, aaa, som);
    KillSwitchService killSwitch =
        new KillSwitchService(
            aaa, som, routes, killState, subscriptions, System::currentTimeMillis);

    ApiSurface api =
        new ApiSurface(aaa, som, routes, subscriptions, (sid, subId, event) -> {}, pipeline);
    BasketService baskets =
        new BasketService(som, routes, new BulkOrderImporter(api), subscriptions);
    // ── Market data over the 18.12 SPI (EMS_MD_FEED: sim | bloomberg-desktop |
    // bloomberg-server) — ONE feed instance powers both halves: the GUI path (ticks → md
    // topics → watchlist/blotter) and the backend path (9.5 benchmarks, 9.1/9.3 quote fabric,
    // P&L marks). With Bloomberg selected, the real FIGIs in the universe (Apple, Microsoft,
    // Toyota, SPY…) tick live; the synthetic demo FIGIs surface per-subscription entitlement
    // failures as feed health — visible, never silent.
    io.crossasset.ems.md.MarketDataFeed feed =
        io.crossasset.ems.md.MarketDataFeeds.fromEnv(System.getenv());
    boolean simulatedFeed = io.crossasset.ems.md.MarketDataFeeds.isSimulated(feed);
    System.out.println(
        "Market data    : " + feed.feedId() + (simulatedFeed ? " (simulated)" : " (LIVE)"));
    MarketDataTopicBridge mdBridge = new MarketDataTopicBridge(subscriptions);
    mdBridge.attachHealth(feed);
    // Desk watchlist (18.14): seeding attaches each symbol to the bridge.
    DeskWatchlist watchlist =
        new DeskWatchlist(
            subscriptions,
            mdBridge,
            feed,
            Set.of(MdField.BID, MdField.ASK, MdField.LAST, MdField.VOLUME));
    for (DemoUniverse.DemoInstrument inst : INSTRUMENTS) {
      watchlist.add("desk-1", inst.figi());
    }

    // ── Backend market-data consumers on the SAME feed (9.1/9.3/9.5) ───────────
    io.crossasset.ems.md.analytics.BenchmarkService benchmarks =
        new io.crossasset.ems.md.analytics.BenchmarkService();
    io.crossasset.ems.md.quote.QuoteServer quoteServer =
        new io.crossasset.ems.md.quote.QuoteServer(
            new io.crossasset.ems.md.quote.SubscriberRegistry());
    PricingService pricingService = new PricingService();
    for (DemoUniverse.DemoInstrument inst : INSTRUMENTS) {
      feed.subscribe(
          inst.figi(),
          Set.of(MdField.BID, MdField.ASK, MdField.LAST, MdField.VOLUME),
          tick -> {
            benchmarks.onTick(tick); // 9.5: VWAP/TWAP/arrival for fat-finger + TCA
            Long last = tick.values().get(MdField.LAST);
            if (last != null) {
              pricingService.recordLive(tick.figi(), last, tick.atMillis()); // P&L marks
            }
            quoteServer.publish( // 9.1: the internal quote fabric (SOR/automation consumers)
                new io.crossasset.ems.md.quote.QuoteServer.QuoteUpdate(
                    "quote." + tick.figi() + ".l1",
                    tick.figi(),
                    tick.values().toString(),
                    tick.atMillis()));
          });
    }
    feed.start();

    // ── Notifications (18.8): fills/rejects/kill alerts to desktop + email/sms ──
    NotificationService notifications = new NotificationService();
    notifications.registerSink(new DesktopSink(subscriptions));
    notifications.registerSink(loggingSink(NotificationService.Channel.EMAIL));
    notifications.registerSink(loggingSink(NotificationService.Channel.SMS));
    notifications.registerRule(
        new NotificationService.Rule(
            "fills-to-desk",
            "blotter",
            NotificationService.Kind.INFO,
            NotificationService.Severity.INFO,
            "fill",
            "desk-1",
            Set.of(NotificationService.Channel.DESKTOP),
            false,
            0,
            10_000L,
            List.of()));
    notifications.registerRule(
        new NotificationService.Rule(
            "rejects-to-supervisor",
            "blotter",
            NotificationService.Kind.ALERT,
            NotificationService.Severity.HIGH,
            null,
            "desk-1",
            Set.of(NotificationService.Channel.DESKTOP, NotificationService.Channel.EMAIL),
            true,
            15 * 60_000L,
            0,
            List.of()));
    notifications.registerRule(
        new NotificationService.Rule(
            "kill-critical",
            "control",
            null,
            NotificationService.Severity.CRITICAL,
            null,
            "desk-1",
            Set.of(
                NotificationService.Channel.DESKTOP,
                NotificationService.Channel.EMAIL,
                NotificationService.Channel.SMS),
            true,
            5 * 60_000L,
            0,
            List.of()));
    // Bridge blotter/control streams into the queue.
    subscriptions.subscribe(
        0L,
        "blotter.fills",
        Long.MAX_VALUE,
        (sid, sub, event) ->
            notifications.publish(
                "blotter",
                NotificationService.Kind.INFO,
                NotificationService.Severity.INFO,
                "fill",
                event.payload(),
                List.of(event.refId()),
                System.currentTimeMillis()));
    subscriptions.subscribe(
        0L,
        "blotter.routes",
        Long.MAX_VALUE,
        (sid, sub, event) -> {
          if (event.payload().contains("\"state\":\"REJECTED\"")) {
            notifications.publish(
                "blotter",
                NotificationService.Kind.ALERT,
                NotificationService.Severity.HIGH,
                "route-rejected",
                event.payload(),
                List.of(event.refId()),
                System.currentTimeMillis());
          }
        });
    subscriptions.subscribe(
        0L,
        KillSwitchService.TOPIC_KILL,
        Long.MAX_VALUE,
        (sid, sub, event) ->
            notifications.publish(
                "control",
                NotificationService.Kind.ALERT,
                NotificationService.Severity.CRITICAL,
                "kill-" + event.type(),
                event.payload(),
                List.of(event.refId()),
                System.currentTimeMillis()));

    // ── Click-to-trade (18.11): EURUSD ESP stream from a mock dealer ────────────
    String eurusd = "BBG0013HJJ31";
    MockEspVenue espVenue = new MockEspVenue("LMAX", 3, 35L);
    EspClickService esp = new EspClickService();
    esp.attach(eurusd, espVenue);
    com.fasterxml.jackson.databind.ObjectMapper espMapper =
        new com.fasterxml.jackson.databind.ObjectMapper();
    espVenue.subscribe(
        eurusd,
        quote -> {
          var node = espMapper.createObjectNode();
          node.put("figi", quote.figi());
          node.put("venueMic", quote.venueMic());
          node.put("quoteId", quote.quoteId());
          node.put("bidPx", quote.bidPx());
          node.put("askPx", quote.askPx());
          node.put("bidQty", quote.bidQty());
          node.put("askQty", quote.askQty());
          node.put("ts", quote.quotedAtMillis());
          node.put("ttl", quote.ttlMillis());
          String payload = node.toString();
          subscriptions.publish("esp", "EspQuoteRow", quote.figi(), payload);
        });
    Thread.ofVirtual()
        .name("esp-pump")
        .start(
            () -> {
              Random espRandom = new Random();
              long mid = 1_0850L;
              while (true) {
                try {
                  mid += Math.round((espRandom.nextDouble() - 0.5) * 4);
                  espVenue.post(
                      eurusd,
                      mid - 2,
                      5_000_000,
                      mid + 2,
                      5_000_000,
                      System.currentTimeMillis(),
                      2_000L);
                  Thread.sleep(400);
                } catch (InterruptedException e) {
                  return;
                }
              }
            });

    RestEdgeBinding binding =
        new RestEdgeBinding(
            aaa,
            api,
            subscriptions,
            secMaster,
            baskets,
            killSwitch,
            notifications,
            null,
            null,
            esp,
            watchlist);
    binding.setIssuerNames(DemoUniverse.ISSUER_NAMES::get); // 18.29: group-by-issuer
    binding.setCurrencyProfiles(DemoUniverse::profileOf); // 18.30: trading/settle/base/quote
    binding.setQuoteStyles(core -> DemoUniverse.quoteStyleOf(core).name()); // 11.18

    // ── 15c3-5 market-access pack (18.5): the canonical control mapping over the LIVE
    // services — the pack the /api/v1/market-access route exports and the pack the tests pin
    // are built from the same standard mapping. Evidence (kill audit journal, risk-limit
    // amendments, Reg SHO attestation) is pulled at export time, never hand-maintained.
    io.crossasset.ems.pretrade.risk.RiskLimits riskLimits =
        new io.crossasset.ems.pretrade.risk.RiskLimits();
    io.crossasset.ems.pretrade.borrow.BorrowService borrowService =
        new io.crossasset.ems.pretrade.borrow.BorrowService(60_000L);
    binding.setMarketAccess(
        io.crossasset.ems.api.control.EmsMarketAccessControls.standard(
            "firm-demo", killSwitch, riskLimits, borrowService, System::currentTimeMillis),
        System::currentTimeMillis);

    // ── RFQ workflow (11.18): mock dealer panel quoting around the demo base px ──
    // Eligibility (user requirement): AXES quotes TIGHTEST but only trades with institutional
    // accounts — for the demo's ACC-* accounts its quote shows greyed on the ladder, never
    // executable. FADE demonstrates the last-look path (quotes tight, fades on confirm).
    java.util.function.ToLongFunction<String> referencePx =
        figi ->
            INSTRUMENTS.stream()
                .filter(i -> i.figi().equals(figi))
                .mapToLong(DemoUniverse.DemoInstrument::basePx)
                .findFirst()
                .orElse(100_0000L);
    long rfqSession =
        ((LogonOutcome.Accepted)
                aaa.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "demo-bot")))
            .session()
            .sessionId();
    io.crossasset.ems.venue.rfq.RfqService rfqService =
        new io.crossasset.ems.venue.rfq.RfqService(
            (rfq, winner) -> {
              // Book the executed quote through the SAME guarded OMS path as any order:
              // it lands in the blotter, P&L, notifications — an execution like any other.
              StageResult staged =
                  som.stage(
                      new OrderRequest(
                          rfq.rfqId(),
                          rfqSession,
                          rfq.rfqId(),
                          rfq.figi(),
                          rfq.side(),
                          winner.qty(),
                          winner.price(),
                          rfq.account(),
                          0));
              if (staged instanceof StageResult.Accepted accepted) {
                String orderId = accepted.order().orderId();
                som.markReady(orderId, rfqSession);
                RouteResult routed =
                    routes.route(
                        new RouteRequest(
                            rfq.rfqId() + "-R",
                            orderId,
                            winner.dealer(),
                            winner.qty(),
                            winner.price(),
                            null));
                if (routed instanceof RouteResult.Routed r) {
                  routes.acknowledgeRoute(r.route().routeId());
                  routes.fullFill(
                      r.route().routeId(), winner.qty(), winner.price(), rfq.rfqId() + "-X");
                }
              }
            },
            rfq -> {},
            (account, dealer) -> !"AXES".equals(dealer) || account.startsWith("ACC-INST"));
    rfqService.addDealer(
        io.crossasset.ems.venue.rfq.MockRfqDealer.firm("AXES", referencePx, 2, 25_000));
    rfqService.addDealer(
        io.crossasset.ems.venue.rfq.MockRfqDealer.fading("FADE", referencePx, 3, 25_000));
    rfqService.addDealer(
        io.crossasset.ems.venue.rfq.MockRfqDealer.firm("GS", referencePx, 5, 25_000));
    rfqService.addDealer(
        io.crossasset.ems.venue.rfq.MockRfqDealer.firm("JPM", referencePx, 8, 25_000));
    binding.setRfq(rfqService, System::currentTimeMillis);

    // ── Real telemetry (18.26): OTLP traces from the demo edge ──────────────────
    // Opt-in: set OTEL_EXPORTER_OTLP_ENDPOINT (or EMS_DEMO_OTEL=1 for localhost:4317) with the
    // observability stack up — every REST request and every demo-bot order lifecycle exports to
    // Jaeger/Grafana/OpenSearch. Off by default so tests/CI stay silent (this was the 13.x gap:
    // only the otel TOY ever initialized the SDK; no service emitted anything).
    io.opentelemetry.api.trace.Tracer tracer = null;
    boolean otelOn =
        System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT") != null
            || "1".equals(System.getenv("EMS_DEMO_OTEL"));
    if (otelOn) {
      EmsOpenTelemetry otel =
          EmsOpenTelemetry.builder("trader-desktop-edge").deploymentEnv("demo").build();
      tracer = otel.getTracer();
      io.opentelemetry.api.trace.Tracer httpTracer = tracer;
      binding.setRequestObserver(
          (method, path, status, startNanos, endNanos) -> {
            java.time.Instant end = java.time.Instant.now();
            java.time.Instant start = end.minusNanos(endNanos - startNanos);
            io.opentelemetry.api.trace.Span span =
                httpTracer
                    .spanBuilder(method + " " + routeTemplate(path))
                    .setStartTimestamp(start)
                    .startSpan();
            span.setAttribute("http.request.method", method);
            span.setAttribute("url.path", path);
            span.setAttribute("http.response.status_code", status);
            if (status >= 500) {
              span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
            }
            span.end(end);
          });
      System.out.println("OTel telemetry : ON (service trader-desktop-edge)");
    }

    RestHttpServer rest = new RestHttpServer(binding, restPort);
    rest.start();
    WsEventStreamServer ws = new WsEventStreamServer(aaa, subscriptions, wsPort);
    ws.start();

    // ── Intraday P&L (18.7): fills feed positions, md ticks feed marks ─────────
    PositionService positionService = new PositionService();
    PnlService pnlService =
        new PnlService(
            positionService,
            pricingService,
            figi -> DemoUniverse.CURRENCY_OF.getOrDefault(figi, "USD"),
            "USD");
    DemoUniverse.FX_TO_USD.forEach(pnlService::setFxRate);
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

    // --quiet (18.19): no scripted demo bot — deterministic world for automated E2E tests
    // (market-data + ESP pumps stay on; tests create their own orders via the API).
    boolean quiet =
        java.util.Arrays.asList(args).contains("--quiet")
            || "1".equals(System.getenv("EMS_DEMO_QUIET"));
    if (quiet) {
      System.out.println("Mode           : QUIET (no demo bot)");
      Thread.currentThread().join();
    } else {
      runDemoScript(
          aaa,
          som,
          routes,
          simulatedFeed ? (SimulatedFeed) feed : null,
          positionService,
          pricingService,
          tracer);
    }
  }

  /** A scripted trading session: staged orders, routes, dripped fills, ticking quotes. */
  private static void runDemoScript(
      InMemoryAaaService aaa,
      io.crossasset.ems.oms.StagedOrderManager som,
      io.crossasset.ems.oms.RouteManager routes,
      io.crossasset.ems.md.@org.jspecify.annotations.Nullable SimulatedFeed feed,
      PositionService positionService,
      PricingService pricingService,
      io.opentelemetry.api.trace.@org.jspecify.annotations.Nullable Tracer tracer)
      throws InterruptedException {
    LogonOutcome logon = aaa.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "demo-bot"));
    long session = ((LogonOutcome.Accepted) logon).session().sessionId();
    Random random = new Random();
    long[] lastPx = INSTRUMENTS.stream().mapToLong(DemoUniverse.DemoInstrument::basePx).toArray();
    long volume = 0;
    int orderSeq = 0;

    while (true) {
      // Quotes tick every cycle (random walk, ±5 bps) — SIMULATOR ONLY: on a live feed
      // (Bloomberg) the bot still trades orders but never fabricates prices.
      for (int i = 0; feed != null && i < INSTRUMENTS.size(); i++) {
        DemoUniverse.DemoInstrument inst = INSTRUMENTS.get(i);
        lastPx[i] += Math.round(lastPx[i] * (random.nextDouble() - 0.5) * 0.001);
        long spread = Math.max(1, lastPx[i] / 2_000);
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
      // Qty in the instrument's natural unit: shares (×100), $1k bond face, FX/IRS notional,
      // futures contracts — so each asset class reads like its own market.
      if (orderSeq < 999 && random.nextInt(4) == 0) {
        orderSeq++;
        int i = random.nextInt(INSTRUMENTS.size());
        DemoUniverse.DemoInstrument inst = INSTRUMENTS.get(i);
        long qty = (random.nextInt(20) + 1) * inst.lotQty();
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
          // One span per order lifecycle (18.26): the internal audit handle (orderId) rides as
          // a span attribute, fills as span events — live demo traffic in Jaeger.
          io.opentelemetry.api.trace.Span span =
              tracer == null
                  ? null
                  : tracer
                      .spanBuilder("demo.order")
                      .setAttribute("order.id", orderId)
                      .setAttribute("order.figi", inst.figi())
                      .setAttribute("order.side", side == 1 ? "BUY" : "SELL")
                      .setAttribute("order.qty", qty)
                      .startSpan();
          som.markReady(orderId, session);
          String venue = inst.venues().get(random.nextInt(inst.venues().size()));
          RouteResult routed =
              routes.route(
                  new RouteRequest("RCL-" + orderSeq, orderId, venue, qty, lastPx[i], null));
          if (routed instanceof RouteResult.Routed r) {
            String routeId = r.route().routeId();
            if (span != null) {
              span.setAttribute("route.id", routeId).setAttribute("route.venue", venue);
            }
            routes.acknowledgeRoute(routeId);
            long remaining = qty;
            int execSeq = 0;
            while (remaining > 0 && random.nextInt(3) > 0) {
              long fillQty = Math.min(remaining, (random.nextInt(5) + 1) * inst.lotQty());
              remaining -= fillQty;
              String execId = "EXEC-" + orderSeq + "-" + ++execSeq;
              if (remaining == 0) {
                routes.fullFill(routeId, fillQty, lastPx[i], execId);
              } else {
                routes.partialFill(routeId, fillQty, lastPx[i], execId);
              }
              if (span != null) {
                span.addEvent(
                    "fill",
                    io.opentelemetry.api.common.Attributes.of(
                        io.opentelemetry.api.common.AttributeKey.stringKey("exec.id"), execId,
                        io.opentelemetry.api.common.AttributeKey.longKey("fill.qty"), fillQty,
                        io.opentelemetry.api.common.AttributeKey.longKey("fill.px"), lastPx[i]));
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
            if (span != null) {
              span.setAttribute("order.cum_qty", qty - remaining);
            }
          }
          if (span != null) {
            span.end();
          }
        }
      }
      Thread.sleep(400);
    }
  }

  /** Collapse path ids so HTTP spans group by route, not by entity (18.26). */
  private static String routeTemplate(String path) {
    return path.replaceAll("^/api/v1/instruments/.+", "/api/v1/instruments/{figi}")
        .replaceAll("^/api/v1/orders/[^/]+/history$", "/api/v1/orders/{id}/history")
        .replaceAll("^/api/v1/routes/[^/]+/history$", "/api/v1/routes/{id}/history")
        .replaceAll("^/api/v1/notifications/[^/]+/ack$", "/api/v1/notifications/{id}/ack")
        .replaceAll("^/api/v1/baskets/[^/]+/wave$", "/api/v1/baskets/{id}/wave");
  }

  /** Demo email/SMS channel: prints to stdout (real adapters are deployment config). */
  private static NotificationService.NotificationSink loggingSink(
      NotificationService.Channel channel) {
    return new NotificationService.NotificationSink() {
      @Override
      public NotificationService.Channel channel() {
        return channel;
      }

      @Override
      public boolean deliver(NotificationService.Notification notification, String audience) {
        System.out.printf(
            "[%s] %s %s -> %s: %s%n",
            channel,
            notification.severity(),
            notification.subject(),
            audience,
            notification.notificationId());
        return true;
      }
    };
  }
}
