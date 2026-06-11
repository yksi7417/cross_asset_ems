/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.aaa.CredentialKind;
import io.crossasset.ems.aaa.InMemoryAaaEventLog;
import io.crossasset.ems.aaa.InMemoryAaaService;
import io.crossasset.ems.aaa.LogonCredentials;
import io.crossasset.ems.aaa.LogonOutcome;
import io.crossasset.ems.fix.FixGateway;
import io.crossasset.ems.fix.FixMessage;
import io.crossasset.ems.fix.FixTags;
import io.crossasset.ems.fix.TraceparentTag;
import io.crossasset.ems.fix.sim.FixVenueSimulator;
import io.crossasset.ems.fix.venue.FixVenueGateway;
import io.crossasset.ems.fsm.generated.OrderFsmState;
import io.crossasset.ems.fsm.generated.RouteFsmState;
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
import io.crossasset.ems.observability.trace.TraceHop;
import io.crossasset.ems.observability.trace.TracePropagator;
import io.crossasset.ems.observability.trace.TraceVerification;
import io.crossasset.ems.observability.trace.TraceVerifier;
import io.crossasset.ems.oms.InMemoryRouteManager;
import io.crossasset.ems.oms.InMemoryStagedOrderManager;
import io.crossasset.ems.oms.MarkReadyResult;
import io.crossasset.ems.oms.Route;
import io.crossasset.ems.oms.RouteRequest;
import io.crossasset.ems.oms.RouteResult;
import io.crossasset.ems.oms.StagedOrder;
import io.crossasset.ems.posttrade.allocation.AccountShare;
import io.crossasset.ems.posttrade.allocation.AllocationEvent.AllocationApplied;
import io.crossasset.ems.posttrade.allocation.AllocationPolicy;
import io.crossasset.ems.posttrade.allocation.AllocationTemplate;
import io.crossasset.ems.posttrade.allocation.Fill;
import io.crossasset.ems.posttrade.allocation.InMemoryAllocationService;
import io.crossasset.ems.posttrade.allocation.RoundingPolicy;
import io.crossasset.ems.posttrade.confirmation.ConfirmationNetwork;
import io.crossasset.ems.posttrade.confirmation.ConfirmationStageHandler;
import io.crossasset.ems.posttrade.confirmation.InMemoryConfirmationService;
import io.crossasset.ems.posttrade.confirmation.MatchTolerance;
import io.crossasset.ems.posttrade.confirmation.MockConfirmationNetwork;
import io.crossasset.ems.posttrade.confirmation.TradeRecord;
import io.crossasset.ems.posttrade.regulatory.InMemoryRegulatoryReportingService;
import io.crossasset.ems.posttrade.regulatory.RegulatorDeterminer;
import io.crossasset.ems.posttrade.regulatory.RegulatoryStageHandler;
import io.crossasset.ems.posttrade.regulatory.ReportableTrade;
import io.crossasset.ems.posttrade.regulatory.ReportingProfile;
import io.crossasset.ems.posttrade.regulatory.TraceMockAdapter;
import io.crossasset.ems.posttrade.stp.InMemoryStpOrchestrator;
import io.crossasset.ems.posttrade.stp.Stage;
import io.crossasset.ems.posttrade.stp.StageOutcome;
import io.crossasset.ems.posttrade.stp.StageProfile;
import io.crossasset.ems.posttrade.stp.StpOrchestrator;
import io.crossasset.ems.posttrade.stp.StpState;
import io.crossasset.ems.transport.session.SequenceRecoveryService;
import io.crossasset.ems.validator.LayeredValidatorPipeline;
import io.crossasset.ems.validator.ValidatorPipeline;
import io.crossasset.ems.venue.Capability;
import io.crossasset.ems.venue.Dialect;
import io.crossasset.ems.venue.RouteManagerVenueEventSink;
import io.crossasset.ems.venue.VenueRef;
import io.crossasset.ems.venue.VenueRouteRequest;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Task 15.2 — the FIX-wire end-to-end smoke.
 *
 * <p>Upgrades the 15.1 chain on every axis the v1 carry-over demanded: the validator is the real
 * AAA-backed layered pipeline (real logon, real security master), routing goes through the real
 * router (route + order FSMs advance via venue events, no direct venue submit), and the venue edge
 * is a <b>real FIX session</b> — the venue-facing gateway (8.2) speaking wire FIX to the venue
 * simulator (11.15), with the W3C trace riding tag 9700 (8.3) across the wire. Fills come back as
 * ExecutionReports, propagate through Route FSM → Order FSM, and feed the full post-trade tail
 * (allocation → STP → confirmation → TRACE-mock). Asserts a single trace ID through every hop and
 * byte-identical replay. The 11.2 in-process-mock path stays green alongside in {@code
 * MvpSmokeTest}.
 */
class FixWireSmokeTest {

  private static final char SOH = '\u0001';
  private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
  private static final String CL_ORD_ID = "CL-WIRE-001";
  private static final String FIGI = "BBG000BLNNH6";
  private static final String VENUE_MIC = "SIMX";
  private static final long QTY = 1_000_000L;
  private static final long PRICE = 9950L;

  private record SmokeResult(
      List<String> eventLog,
      TraceVerification trace,
      StpState stp,
      StagedOrder finalOrder,
      Route finalRoute,
      List<String> venueInboundWire) {}

  /** Runs the full wire chain once; pure function of its inputs → replay-comparable log. */
  private SmokeResult runPipeline() {
    List<String> log = new ArrayList<>();
    TracePropagator traces = new TracePropagator();
    TraceVerifier verifier = new TraceVerifier();

    // ── Real AAA + security master + layered validator (no permissive stubs). ──
    InMemoryAaaService aaa =
        new InMemoryAaaService(
            new InMemoryAaaEventLog(), null, new SequenceRecoveryService(() -> 0L));
    InMemorySecurityMasterService secMaster = new InMemorySecurityMasterService();
    ValidatorPipeline pipeline = new LayeredValidatorPipeline(aaa, secMaster, null);
    aaa.registerCredential("tok-wire", "firm-a", "desk-1", "trader-1", Set.of());
    long sessionId =
        ((LogonOutcome.Accepted)
                aaa.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "tok-wire")))
            .session()
            .sessionId();
    publishInstrument(secMaster);

    InMemoryStagedOrderManager som = new InMemoryStagedOrderManager(pipeline);
    InMemoryRouteManager router = new InMemoryRouteManager(som);

    // ── Client FIX edge (8.1 + 8.3): tag 9700 on the inbound NOS is adopted as the trace. ──
    List<String> clientWire = new ArrayList<>();
    FixGateway clientGateway =
        new FixGateway(
            som,
            new SequenceRecoveryService(() -> 0L),
            (sid, seq, raw) -> clientWire.add(raw),
            "EMS",
            "CLIENT",
            traces,
            sid -> null);
    clientGateway.onLogon(sessionId, 1L);
    clientGateway.onInbound(sessionId, newOrderSingle(sessionId));
    verifier.observe(CL_ORD_ID, TraceHop.FIX_IN, traces.lookup(CL_ORD_ID).orElseThrow());
    verifier.observe(CL_ORD_ID, TraceHop.VALIDATE, traces.lookup(CL_ORD_ID).orElseThrow());
    verifier.observe(CL_ORD_ID, TraceHop.STAGE, traces.lookup(CL_ORD_ID).orElseThrow());

    FixMessage clientEr =
        clientWire.stream()
            .map(FixMessage::parse)
            .filter(m -> "8".equals(m.get(FixTags.MSG_TYPE)))
            .findFirst()
            .orElseThrow();
    String orderId = clientEr.get(FixTags.ORDER_ID);
    log.add("EXEC_REPORT ordStatus=" + clientEr.get(FixTags.ORD_STATUS) + " orderId=" + orderId);

    // ── Real router path: markReady → route (route FSM born, qty checked). ──
    MarkReadyResult ready = som.markReady(orderId, sessionId);
    assertThat(ready).isInstanceOf(MarkReadyResult.Ready.class);
    RouteResult routeResult =
        router.route(new RouteRequest("req-wire", orderId, VENUE_MIC, QTY, PRICE, null));
    Route route = ((RouteResult.Routed) routeResult).route();
    String routeClOrdId = route.fsmContext().clOrdId();
    traces.alias(routeClOrdId, CL_ORD_ID); // identity chaining: order → route
    verifier.observe(CL_ORD_ID, TraceHop.ROUTE, traces.lookup(routeClOrdId).orElseThrow());
    log.add("ROUTED routeId=" + route.routeId() + " clOrdId=" + routeClOrdId);

    // ── The wire: venue gateway (8.2) ⇄ venue simulator (11.15), trace on tag 9700. ──
    List<String> venueInbound = new ArrayList<>(); // what the venue receives (assert tag 9700)
    FixVenueGateway[] gatewayRef = new FixVenueGateway[1];
    FixVenueSimulator simulator =
        new FixVenueSimulator(
            "SIMX",
            "EMS",
            FixVenueSimulator.ExecutionModel.fullFill(PRICE),
            raw -> gatewayRef[0].onInbound(raw)); // venue → gateway (fills come back)
    FixVenueGateway venueGateway =
        new FixVenueGateway(
            new VenueRef("venue-sim", VENUE_MIC, Dialect.FIX),
            EnumSet.of(Capability.SUPPORTS_LIMIT, Capability.SUPPORTS_MARKET),
            new RouteManagerVenueEventSink(router), // ERs drive Route FSM → Order FSM
            false,
            new SequenceRecoveryService(() -> 0L),
            7_000L,
            (sid, seq, raw) -> {
              venueInbound.add(raw);
              simulator.onInbound(raw); // gateway → venue
            },
            "EMS",
            "SIMX",
            30,
            traces,
            true); // trace-aware venue: stamp tag 9700
    gatewayRef[0] = venueGateway;

    venueGateway.connect(1);
    venueGateway.submit(new VenueRouteRequest(route.routeId(), routeClOrdId, FIGI, 1, QTY, PRICE));
    verifier.observe(CL_ORD_ID, TraceHop.VENUE_OUT, traces.lookup(routeClOrdId).orElseThrow());

    // Fills propagated synchronously over the in-process wire: order + route FSMs are done.
    StagedOrder finalOrder = som.findOrder(orderId).orElseThrow();
    Route finalRoute = router.findRoute(route.routeId()).orElseThrow();
    log.add("ROUTE_STATE " + finalRoute.fsmState() + " cum=" + finalRoute.fsmContext().cumQty());
    log.add("ORDER_STATE " + finalOrder.fsmState() + " cum=" + finalOrder.fsmContext().cumQty());

    // ── Post-trade tail (12.1 → 12.2 → 12.3 → 12.6), keyed off the real route fill. ──
    InMemoryStpOrchestrator stp = new InMemoryStpOrchestrator(new InMemoryAllocationService());
    TradeRecord ourTrade =
        new TradeRecord(
            route.routeId(), FIGI, 1, QTY, PRICE, 0, "2026-06-10", "2026-06-12", "CPTY-X");
    ConfirmationNetwork confNet =
        MockConfirmationNetwork.marketAxessPostTrade().withCounterpartyRecord(ourTrade);
    stp.register(
        Stage.CONFIRMATION,
        new ConfirmationStageHandler(
            new InMemoryConfirmationService(), confNet, MatchTolerance.exact(), ctx -> ourTrade));

    InMemoryRegulatoryReportingService reg =
        new InMemoryRegulatoryReportingService(RegulatorDeterminer.usDefaults());
    reg.registerProfile(ReportingProfile.trace());
    reg.registerAdapter(TraceMockAdapter.acking());
    ReportableTrade reportable =
        new ReportableTrade(
            "TR-" + route.routeId(), "CORP_BOND", "US", FIGI, 1, QTY, PRICE, traceFields());
    stp.register(Stage.REGULATORY_REPORTING, new RegulatoryStageHandler(reg, ctx -> reportable));

    AllocationTemplate template =
        AllocationTemplate.of(
            "TPL-WIRE",
            1L,
            AllocationPolicy.PRO_RATA,
            RoundingPolicy.DISTRIBUTE_RESIDUAL,
            List.of(
                new AccountShare("ACC_A", "PB1", 6000), new AccountShare("ACC_B", "PB1", 4000)));
    Fill fill =
        new Fill("SIMX-X1", orderId, route.routeId(), finalRoute.fsmContext().cumQty(), PRICE);
    StpOrchestrator.StpResult stpResult = stp.ingest(fill, template, StageProfile.corpBond());

    for (var event : stpResult.allocationEvents()) {
      if (event instanceof AllocationApplied applied) {
        log.add(
            "ALLOC acct=" + applied.account() + " qty=" + applied.qty() + " px=" + applied.price());
      }
    }
    for (Stage stage : StageProfile.corpBond().downstreamStages()) {
      log.add("STAGE " + stage + "=" + stpResult.state().stages().get(stage));
    }
    log.add("OVERALL=" + stpResult.state().overall());

    TraceVerification trace = verifier.verify(CL_ORD_ID, EnumSet.allOf(TraceHop.class));
    log.add("TRACE id=" + trace.traceId() + " single=" + trace.singleTraceId());

    return new SmokeResult(log, trace, stpResult.state(), finalOrder, finalRoute, venueInbound);
  }

  // ── Assertions ───────────────────────────────────────────────────────────────

  @Test
  void wireEndToEnd_realSessionRealRouterSingleTrace() {
    SmokeResult result = runPipeline();

    // The venue saw a real FIX session: logon then a NOS carrying tag 9700 with OUR trace.
    List<FixMessage> venueSaw = result.venueInboundWire().stream().map(FixMessage::parse).toList();
    assertThat(venueSaw.get(0).get(FixTags.MSG_TYPE)).isEqualTo("A");
    FixMessage nos =
        venueSaw.stream()
            .filter(m -> "D".equals(m.get(FixTags.MSG_TYPE)))
            .findFirst()
            .orElseThrow();
    assertThat(TraceparentTag.decodeTraceId(nos.get(TraceparentTag.TAG))).contains(TRACE_ID);

    // Fills returned over the wire drove the REAL FSMs to terminal.
    assertThat(result.finalRoute().fsmState()).isEqualTo(RouteFsmState.FILLED);
    assertThat(result.finalRoute().fsmContext().cumQty()).isEqualTo(QTY);
    assertThat(result.finalOrder().fsmState()).isEqualTo(OrderFsmState.FILLED);

    // Single trace ID across FIX-in → validate → stage → route → venue-out.
    assertThat(result.trace().traceId()).isEqualTo(TRACE_ID);
    assertThat(result.trace().singleTraceId()).isTrue();
    assertThat(result.trace().complete()).isTrue();

    // Post-trade tail complete off the wire fill.
    assertThat(result.stp().stages().get(Stage.CONFIRMATION)).isEqualTo(StageOutcome.COMPLETE);
    assertThat(result.stp().stages().get(Stage.REGULATORY_REPORTING))
        .isEqualTo(StageOutcome.COMPLETE);
    assertThat(result.stp().overall()).isEqualTo(StpState.Overall.COMPLETE);
    assertThat(result.eventLog()).contains("ALLOC acct=ACC_A qty=600000 px=9950");
    assertThat(result.eventLog()).contains("ALLOC acct=ACC_B qty=400000 px=9950");
  }

  @Test
  void replay_isByteIdentical() {
    SmokeResult first = runPipeline();
    SmokeResult second = runPipeline();
    assertThat(second.eventLog()).isEqualTo(first.eventLog());
    assertThat(second.venueInboundWire()).isEqualTo(first.venueInboundWire());
  }

  // ── helpers ─────────────────────────────────────────────────────────────────

  private String newOrderSingle(long sessionId) {
    String tag9700 = TraceparentTag.encode(TraceparentTag.traceparentFor(TRACE_ID, CL_ORD_ID));
    return "8=FIX.4.4"
        + SOH
        + "35=D"
        + SOH
        + "34=1"
        + SOH
        + "49=CLIENT"
        + SOH
        + "56=EMS"
        + SOH
        + "11="
        + CL_ORD_ID
        + SOH
        + "48="
        + FIGI
        + SOH
        + "54=1"
        + SOH
        + "38="
        + QTY
        + SOH
        + "44="
        + PRICE
        + SOH
        + "1=ACC_A"
        + SOH
        + "59=0"
        + SOH
        + "9700="
        + tag9700
        + SOH
        + "10=000"
        + SOH;
  }

  private static void publishInstrument(InMemorySecurityMasterService secMaster) {
    InstrumentCore core =
        new InstrumentCore(
            FIGI,
            "IID-WIRE",
            null,
            null,
            AssetClass.FIXED_INCOME,
            InstrumentType.CORPORATE_SENIOR,
            "Wire Corp 5.25 2031",
            "Wire Corp",
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
    secMaster.publish(
        SecurityMasterSnapshot.EMPTY.apply(
            new SecurityMasterEvent.InstrumentCreated(new InstrumentVersioned(core, null), 1L)));
  }

  private Map<String, String> traceFields() {
    Map<String, String> f = new HashMap<>();
    f.put("trace_party_id", "BD123");
    f.put("cusip_or_isin", FIGI);
    f.put("executing_broker", "EB1");
    f.put("contra_party_id", "C");
    f.put("side", "1");
    f.put("qty", Long.toString(QTY));
    f.put("price", Long.toString(PRICE));
    f.put("yield", "452");
    f.put("trade_date", "2026-06-10");
    f.put("settle_date", "2026-06-12");
    return f;
  }
}
