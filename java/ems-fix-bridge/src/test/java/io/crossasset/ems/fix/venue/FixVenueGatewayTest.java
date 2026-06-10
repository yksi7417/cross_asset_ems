/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix.venue;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.fix.FixMessage;
import io.crossasset.ems.fix.FixTags;
import io.crossasset.ems.transport.session.SequenceRecoveryService;
import io.crossasset.ems.venue.Capability;
import io.crossasset.ems.venue.Dialect;
import io.crossasset.ems.venue.VenueEventSink;
import io.crossasset.ems.venue.VenueRef;
import io.crossasset.ems.venue.VenueRouteRequest;
import io.crossasset.ems.venue.VenueState;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FixVenueGateway}: outbound 35=D/F/G encoding over the resumable session channel,
 * inbound ExecutionReport / OrderCancelReject mapping onto the {@link VenueEventSink}, gap
 * handling, heartbeat replies, and shadow mode. Per arch-fix-api-bridge.md, task 8.2.
 */
class FixVenueGatewayTest {

  private static final long VENUE_SESSION = 7_000L;
  private static final String FIGI = "BBG00FIXVENUE1";

  private RecordingSink sink;
  private List<String> wire;
  private FixVenueGateway gateway;
  private long venueSeq; // sequence of messages the "venue" sends us

  @BeforeEach
  void setUp() {
    sink = new RecordingSink();
    wire = new ArrayList<>();
    SequenceRecoveryService sessions = new SequenceRecoveryService(() -> 0L);
    gateway =
        new FixVenueGateway(
            new VenueRef("venue-sim", "SIMX", Dialect.FIX),
            EnumSet.of(
                Capability.SUPPORTS_MARKET,
                Capability.SUPPORTS_LIMIT,
                Capability.SUPPORTS_CANCEL,
                Capability.SUPPORTS_REPLACE),
            sink,
            false,
            sessions,
            VENUE_SESSION,
            (sessionId, seq, rawFix) -> wire.add(rawFix),
            "EMS",
            "SIMX",
            30);
    gateway.connect(1);
    venueSeq = 1;
  }

  // ── Session ──────────────────────────────────────────────────────────────────

  @Test
  void connect_sendsLogonAndMarksConnected() {
    assertThat(gateway.state()).isEqualTo(VenueState.CONNECTED);
    FixMessage logon = FixMessage.parse(wire.get(0));
    assertThat(logon.get(FixTags.MSG_TYPE)).isEqualTo("A");
    assertThat(logon.get(FixTags.HEART_BT_INT)).isEqualTo("30");
    assertThat(logon.get(FixTags.MSG_SEQ_NUM)).isEqualTo("1");
    assertThat(logon.get(FixTags.SENDER_COMP_ID)).isEqualTo("EMS");
    assertThat(logon.get(FixTags.TARGET_COMP_ID)).isEqualTo("SIMX");
  }

  @Test
  void inboundTestRequest_answeredWithHeartbeatEchoingId() {
    deliver(venueMsg("1").field(FixTags.TEST_REQ_ID, "PING-7"));
    FixMessage reply = FixMessage.parse(wire.get(wire.size() - 1));
    assertThat(reply.get(FixTags.MSG_TYPE)).isEqualTo("0");
    assertThat(reply.get(FixTags.TEST_REQ_ID)).isEqualTo("PING-7");
  }

  @Test
  void inboundLogout_marksReconnecting() {
    deliver(venueMsg("5"));
    assertThat(gateway.state()).isEqualTo(VenueState.RECONNECTING);
  }

  // ── Outbound ─────────────────────────────────────────────────────────────────

  @Test
  void submit_encodesLimitNewOrderSingle() {
    gateway.submit(new VenueRouteRequest("RTE-1", "CL-1", FIGI, 1, 500_000, 101_250L));
    FixMessage nos = FixMessage.parse(wire.get(wire.size() - 1));
    assertThat(nos.get(FixTags.MSG_TYPE)).isEqualTo("D");
    assertThat(nos.get(FixTags.CL_ORD_ID)).isEqualTo("CL-1");
    assertThat(nos.get(FixTags.SECURITY_ID)).isEqualTo(FIGI);
    assertThat(nos.get(FixTags.SIDE)).isEqualTo("1");
    assertThat(nos.get(FixTags.ORDER_QTY)).isEqualTo("500000");
    assertThat(nos.get(FixTags.ORD_TYPE)).isEqualTo("2");
    assertThat(nos.get(FixTags.PRICE)).isEqualTo("101250");
    assertThat(nos.get(FixTags.MSG_SEQ_NUM)).isEqualTo("2"); // logon took seq 1
  }

  @Test
  void submit_marketOrder_omitsPrice() {
    gateway.submit(new VenueRouteRequest("RTE-1", "CL-1", FIGI, 2, 100, null));
    FixMessage nos = FixMessage.parse(wire.get(wire.size() - 1));
    assertThat(nos.get(FixTags.ORD_TYPE)).isEqualTo("1");
    assertThat(nos.has(FixTags.PRICE)).isFalse();
  }

  @Test
  void cancel_encodesCancelRequestWithOrigClOrdId() {
    gateway.submit(new VenueRouteRequest("RTE-1", "CL-1", FIGI, 1, 100, null));
    gateway.cancel("RTE-1");
    FixMessage cxl = FixMessage.parse(wire.get(wire.size() - 1));
    assertThat(cxl.get(FixTags.MSG_TYPE)).isEqualTo("F");
    assertThat(cxl.get(FixTags.ORIG_CL_ORD_ID)).isEqualTo("CL-1");
    assertThat(cxl.get(FixTags.CL_ORD_ID)).startsWith("CL-1.C");
  }

  @Test
  void replace_encodesCancelReplace() {
    gateway.submit(new VenueRouteRequest("RTE-1", "CL-1", FIGI, 1, 100, 99_000L));
    gateway.replace("RTE-1", "CL-1-R1", 150, 98_500L);
    FixMessage rpl = FixMessage.parse(wire.get(wire.size() - 1));
    assertThat(rpl.get(FixTags.MSG_TYPE)).isEqualTo("G");
    assertThat(rpl.get(FixTags.ORIG_CL_ORD_ID)).isEqualTo("CL-1");
    assertThat(rpl.get(FixTags.CL_ORD_ID)).isEqualTo("CL-1-R1");
    assertThat(rpl.get(FixTags.ORDER_QTY)).isEqualTo("150");
    assertThat(rpl.get(FixTags.PRICE)).isEqualTo("98500");
  }

  @Test
  void shadowMode_discardsAllOutbound() {
    List<String> shadowWire = new ArrayList<>();
    FixVenueGateway shadow =
        new FixVenueGateway(
            new VenueRef("venue-shadow", "SHDW", Dialect.FIX),
            EnumSet.of(Capability.SUPPORTS_LIMIT),
            sink,
            true,
            new SequenceRecoveryService(() -> 0L),
            8_000L,
            (sessionId, seq, rawFix) -> shadowWire.add(rawFix),
            "EMS",
            "SHDW",
            30);
    shadow.connect(1);
    shadow.submit(new VenueRouteRequest("RTE-S", "CL-S", FIGI, 1, 100, null));
    shadow.cancel("RTE-S");
    assertThat(shadowWire).isEmpty();
    assertThat(shadow.state()).isEqualTo(VenueState.CONNECTED);
  }

  // ── Inbound ExecutionReports ─────────────────────────────────────────────────

  @Test
  void execReport_new_acknowledgesRoute() {
    submitRoute("RTE-1", "CL-1");
    deliver(execReport("CL-1", "0", "0"));
    assertThat(sink.events).containsExactly("ack:RTE-1");
  }

  @Test
  void execReport_pendingNew_surfacesPendingNew() {
    submitRoute("RTE-1", "CL-1");
    deliver(execReport("CL-1", "A", "A"));
    assertThat(sink.events).containsExactly("pendingNew:RTE-1");
  }

  @Test
  void execReport_rejected_carriesVenueText() {
    submitRoute("RTE-1", "CL-1");
    deliver(execReport("CL-1", "8", "8").field(FixTags.TEXT, "unknown instrument"));
    assertThat(sink.events).containsExactly("rejected:RTE-1:unknown instrument");
  }

  @Test
  void execReport_partialFill_mapsQtyPxExecId() {
    submitRoute("RTE-1", "CL-1");
    deliver(
        execReport("CL-1", "F", "1")
            .field(FixTags.LAST_QTY, 40)
            .field(FixTags.LAST_PX, 101_000L)
            .field(FixTags.EXEC_ID, "X-77"));
    assertThat(sink.events).containsExactly("partial:RTE-1:40:101000:X-77");
  }

  @Test
  void execReport_fullFill_mapsToFilled() {
    submitRoute("RTE-1", "CL-1");
    deliver(
        execReport("CL-1", "F", "2")
            .field(FixTags.LAST_QTY, 100)
            .field(FixTags.LAST_PX, 101_500L)
            .field(FixTags.EXEC_ID, "X-78"));
    assertThat(sink.events).containsExactly("filled:RTE-1:100:101500:X-78");
  }

  @Test
  void execReport_canceled_afterCancelRequest() {
    submitRoute("RTE-1", "CL-1");
    gateway.cancel("RTE-1");
    deliver(execReport("CL-1", "4", "4"));
    assertThat(sink.events).containsExactly("canceled:RTE-1");
  }

  @Test
  void execReport_replaced_updatesCurrentClOrdId() {
    submitRoute("RTE-1", "CL-1");
    gateway.replace("RTE-1", "CL-1-R1", 150, null);
    deliver(execReport("CL-1-R1", "5", "0"));
    assertThat(sink.events).containsExactly("replaced:RTE-1");
    // Subsequent cancel must reference the replacement ClOrdID.
    gateway.cancel("RTE-1");
    FixMessage cxl = FixMessage.parse(wire.get(wire.size() - 1));
    assertThat(cxl.get(FixTags.ORIG_CL_ORD_ID)).isEqualTo("CL-1-R1");
  }

  @Test
  void execReport_unknownClOrdId_isIgnored() {
    deliver(execReport("CL-UNKNOWN", "0", "0"));
    assertThat(sink.events).isEmpty();
  }

  // ── Inbound OrderCancelReject ────────────────────────────────────────────────

  @Test
  void orderCancelReject_responseToCancel_mapsCancelRejected() {
    submitRoute("RTE-1", "CL-1");
    deliver(
        venueMsg("9")
            .field(FixTags.ORIG_CL_ORD_ID, "CL-1")
            .field(FixTags.CXL_REJ_RESPONSE_TO, "1")
            .field(FixTags.CXL_REJ_REASON, 1));
    assertThat(sink.events).containsExactly("cancelRejected:RTE-1:1");
  }

  @Test
  void orderCancelReject_responseToReplace_mapsReplaceRejected() {
    submitRoute("RTE-1", "CL-1");
    deliver(
        venueMsg("9")
            .field(FixTags.ORIG_CL_ORD_ID, "CL-1")
            .field(FixTags.CXL_REJ_RESPONSE_TO, "2")
            .field(FixTags.CXL_REJ_REASON, 2));
    assertThat(sink.events).containsExactly("replaceRejected:RTE-1:2");
  }

  // ── Tag 9700 trace propagation (8.3) ─────────────────────────────────────────

  @Test
  void submit_traceAwareVenue_stampsTag9700() {
    io.crossasset.ems.observability.trace.TracePropagator traces =
        new io.crossasset.ems.observability.trace.TracePropagator();
    List<String> tWire = new ArrayList<>();
    FixVenueGateway aware = traceGateway(traces, true, tWire);
    String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
    traces.stamp("CL-T1", traceId);
    aware.submit(new VenueRouteRequest("RTE-T1", "CL-T1", FIGI, 1, 100, null));
    FixMessage nos = FixMessage.parse(tWire.get(tWire.size() - 1));
    String tagValue = nos.get(io.crossasset.ems.fix.TraceparentTag.TAG);
    assertThat(tagValue).isNotNull();
    assertThat(io.crossasset.ems.fix.TraceparentTag.decodeTraceId(tagValue)).contains(traceId);
  }

  @Test
  void submit_nonTraceAwareVenue_withholdsTagButKeepsRejoinMap() {
    io.crossasset.ems.observability.trace.TracePropagator traces =
        new io.crossasset.ems.observability.trace.TracePropagator();
    List<String> tWire = new ArrayList<>();
    FixVenueGateway blind = traceGateway(traces, false, tWire);
    traces.stamp("CL-T2", "4bf92f3577b34da6a3ce929d0e0e4736");
    blind.submit(new VenueRouteRequest("RTE-T2", "CL-T2", FIGI, 1, 100, null));
    FixMessage nos = FixMessage.parse(tWire.get(tWire.size() - 1));
    assertThat(nos.has(io.crossasset.ems.fix.TraceparentTag.TAG)).isFalse();
    assertThat(traces.lookup("CL-T2")).isPresent();
  }

  @Test
  void replaceAndCancel_aliasTraceToNewClOrdIds() {
    io.crossasset.ems.observability.trace.TracePropagator traces =
        new io.crossasset.ems.observability.trace.TracePropagator();
    FixVenueGateway aware = traceGateway(traces, true, new ArrayList<>());
    String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
    traces.stamp("CL-T3", traceId);
    aware.submit(new VenueRouteRequest("RTE-T3", "CL-T3", FIGI, 1, 100, null));
    aware.replace("RTE-T3", "CL-T3-R1", 150, null);
    assertThat(traces.lookup("CL-T3-R1")).contains(traceId);
    aware.cancel("RTE-T3");
    assertThat(traces.lookup("CL-T3.C1")).contains(traceId);
  }

  private FixVenueGateway traceGateway(
      io.crossasset.ems.observability.trace.TracePropagator traces,
      boolean traceAware,
      List<String> targetWire) {
    FixVenueGateway g =
        new FixVenueGateway(
            new VenueRef("venue-trace", "TRCV", Dialect.FIX),
            EnumSet.of(Capability.SUPPORTS_MARKET, Capability.SUPPORTS_LIMIT),
            sink,
            false,
            new SequenceRecoveryService(() -> 0L),
            9_000L,
            (sessionId, seq, rawFix) -> targetWire.add(rawFix),
            "EMS",
            "TRCV",
            30,
            traces,
            traceAware);
    g.connect(1);
    return g;
  }

  // ── Gaps ─────────────────────────────────────────────────────────────────────

  @Test
  void inboundGap_messageNotProcessed() {
    submitRoute("RTE-1", "CL-1");
    venueSeq = 5; // skip ahead: venue jumps from 1 to 5
    deliver(execReport("CL-1", "0", "0"));
    assertThat(sink.events).isEmpty();
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private void submitRoute(String routeId, String clOrdId) {
    gateway.submit(new VenueRouteRequest(routeId, clOrdId, FIGI, 1, 100, null));
  }

  /** Builder pre-loaded with the venue's standard header at the next venue sequence. */
  private FixMessage.Builder venueMsg(String msgType) {
    return FixMessage.builder()
        .field(FixTags.MSG_TYPE, msgType)
        .field(FixTags.MSG_SEQ_NUM, venueSeq)
        .field(FixTags.SENDER_COMP_ID, "SIMX")
        .field(FixTags.TARGET_COMP_ID, "EMS");
  }

  private FixMessage.Builder execReport(String clOrdId, String execType, String ordStatus) {
    return venueMsg("8")
        .field(FixTags.CL_ORD_ID, clOrdId)
        .field(FixTags.EXEC_TYPE, execType)
        .field(FixTags.ORD_STATUS, ordStatus);
  }

  private void deliver(FixMessage.Builder builder) {
    gateway.onInbound(builder.build());
    venueSeq++;
  }

  /** Records every sink callback as a compact string for order-sensitive assertions. */
  private static final class RecordingSink implements VenueEventSink {
    final List<String> events = new ArrayList<>();

    @Override
    public void acknowledged(String routeId) {
      events.add("ack:" + routeId);
    }

    @Override
    public void pendingNew(String routeId) {
      events.add("pendingNew:" + routeId);
    }

    @Override
    public void rejected(String routeId, @Nullable String venueReason) {
      events.add("rejected:" + routeId + ":" + venueReason);
    }

    @Override
    public void partialFill(String routeId, long lastQty, long lastPx, String execId) {
      events.add("partial:" + routeId + ":" + lastQty + ":" + lastPx + ":" + execId);
    }

    @Override
    public void filled(String routeId, long lastQty, long lastPx, String execId) {
      events.add("filled:" + routeId + ":" + lastQty + ":" + lastPx + ":" + execId);
    }

    @Override
    public void canceled(String routeId) {
      events.add("canceled:" + routeId);
    }

    @Override
    public void cancelRejected(String routeId, int cxlRejReason) {
      events.add("cancelRejected:" + routeId + ":" + cxlRejReason);
    }

    @Override
    public void replaced(String routeId) {
      events.add("replaced:" + routeId);
    }

    @Override
    public void replaceRejected(String routeId, int cxlRejReason) {
      events.add("replaceRejected:" + routeId + ":" + cxlRejReason);
    }
  }
}
