/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.fsm.generated.OrderFsmContext;
import io.crossasset.ems.fsm.generated.OrderFsmEvent;
import io.crossasset.ems.fsm.generated.OrderFsmState;
import io.crossasset.ems.oms.AmendFields;
import io.crossasset.ems.oms.AmendResult;
import io.crossasset.ems.oms.CancelResult;
import io.crossasset.ems.oms.MarkReadyResult;
import io.crossasset.ems.oms.OrderRequest;
import io.crossasset.ems.oms.OrderSubState;
import io.crossasset.ems.oms.StageResult;
import io.crossasset.ems.oms.StagedOrder;
import io.crossasset.ems.oms.StagedOrderManager;
import io.crossasset.ems.transport.session.SequenceRecoveryService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FixGateway} orchestration: NewOrderSingle happy path, reject paths, and the
 * three 8.9 consumer contracts (reconnect calls logon+resumeOutbound; RESET on an evicted hole; one
 * TestRequest per heartbeat window, cleared by inbound activity).
 */
class FixGatewayTest {

  static final char SOH = '\u0001';
  static final long SESSION = 100L;

  // ── Test doubles ─────────────────────────────────────────────────────────────

  /** Captures every outbound delivery for assertions. */
  static final class RecordingSink implements OutboundSink {
    record Sent(long sessionId, long seq, String wire) {}

    final List<Sent> sent = new ArrayList<>();

    @Override
    public void deliver(long sessionId, long outboundSeq, String rawFix) {
      sent.add(new Sent(sessionId, outboundSeq, rawFix));
    }

    List<FixMessage> ofType(String msgType) {
      List<FixMessage> out = new ArrayList<>();
      for (Sent s : sent) {
        FixMessage m = FixMessage.parse(s.wire());
        if (msgType.equals(m.get(FixTags.MSG_TYPE))) {
          out.add(m);
        }
      }
      return out;
    }
  }

  /** Stages orders without the validator: rejects ClOrdIDs beginning "REJ", accepts the rest. */
  static final class FakeOms implements StagedOrderManager {
    @Override
    public StageResult stage(OrderRequest r) {
      if (r.clOrdId().startsWith("REJ")) {
        return new StageResult.Rejected(r.requestId(), "EMS-VAL-2001", "Validator rejected order");
      }
      return new StageResult.Accepted(newOrder(r));
    }

    private StagedOrder newOrder(OrderRequest r) {
      String orderId = "EMS-ORD-" + r.clOrdId();
      OrderFsmContext ctx =
          new OrderFsmContext(
              orderId,
              r.clOrdId(),
              null,
              r.figi(),
              r.side(),
              r.qty(),
              r.price(),
              0L,
              r.qty(),
              r.account(),
              r.tif(),
              r.clOrdId(),
              orderId,
              1L,
              null,
              null);
      return new StagedOrder(
          orderId,
          r.clOrdId(),
          r.sessionId(),
          OrderFsmState.NEW,
          ctx,
          OrderSubState.NEW,
          Set.of(),
          0L);
    }

    @Override
    public AmendResult amend(String orderId, AmendFields fields, long sessionId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CancelResult cancel(String orderId, long sessionId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public MarkReadyResult markReady(String orderId, long sessionId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setPendingActionDone(String orderId, String actionRef) {}

    @Override
    public Optional<StagedOrder> findOrder(String orderId) {
      return Optional.empty();
    }

    @Override
    public java.util.List<StagedOrder> activeOrders() {
      return java.util.List.of();
    }

    @Override
    public Optional<StagedOrder> applyOrderFsmEvent(
        String orderId, OrderFsmEvent event, Object payload) {
      return Optional.empty();
    }

    @Override
    public Optional<StagedOrder> markRouting(String orderId) {
      return Optional.empty();
    }
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────

  private String newOrderSingle(long seq, String clOrdId, String extra) {
    StringBuilder sb = new StringBuilder();
    sb.append("8=FIX.4.4").append(SOH).append("35=D").append(SOH);
    sb.append("34=").append(seq).append(SOH);
    sb.append("49=CLIENT").append(SOH).append("56=EMS").append(SOH);
    sb.append("11=").append(clOrdId).append(SOH);
    sb.append("48=BBG000BLNNH6").append(SOH);
    sb.append("54=1").append(SOH).append("38=100").append(SOH).append("1=ACC1").append(SOH);
    if (extra != null) {
      sb.append(extra).append(SOH);
    }
    sb.append("10=000").append(SOH);
    return sb.toString();
  }

  private String control(String msgType, long seq) {
    return FixMessage.builder()
        .field(FixTags.MSG_TYPE, msgType)
        .field(FixTags.MSG_SEQ_NUM, seq)
        .field(FixTags.SENDER_COMP_ID, "CLIENT")
        .field(FixTags.TARGET_COMP_ID, "EMS")
        .build();
  }

  private FixGateway gateway(SequenceRecoveryService svc, RecordingSink sink) {
    return new FixGateway(new FakeOms(), svc, sink, "EMS", "CLIENT");
  }

  // ── NewOrderSingle happy path + rejects ──────────────────────────────────────

  @Test
  void newOrderSingle_accepted_emitsExecutionReport() {
    SequenceRecoveryService svc = new SequenceRecoveryService(() -> 0L);
    RecordingSink sink = new RecordingSink();
    FixGateway gw = gateway(svc, sink);

    gw.onLogon(SESSION, 1L);
    gw.onInbound(SESSION, newOrderSingle(1, "CL001", "44=9950"));

    List<FixMessage> reports = sink.ofType("8");
    assertThat(reports).hasSize(1);
    FixMessage er = reports.get(0);
    assertThat(er.get(FixTags.ORD_STATUS)).isEqualTo("0");
    assertThat(er.get(FixTags.EXEC_TYPE)).isEqualTo("0");
    assertThat(er.get(FixTags.CL_ORD_ID)).isEqualTo("CL001");
    assertThat(er.get(FixTags.ORDER_ID)).isEqualTo("EMS-ORD-CL001");
  }

  @Test
  void newOrderSingle_validatorRejected_emitsBusinessReject() {
    SequenceRecoveryService svc = new SequenceRecoveryService(() -> 0L);
    RecordingSink sink = new RecordingSink();
    FixGateway gw = gateway(svc, sink);

    gw.onLogon(SESSION, 1L);
    gw.onInbound(SESSION, newOrderSingle(1, "REJ-1", "44=9950"));

    assertThat(sink.ofType("8")).isEmpty();
    List<FixMessage> rejects = sink.ofType("j");
    assertThat(rejects).hasSize(1);
    assertThat(rejects.get(0).get(FixTags.TEXT)).contains("EMS-VAL-2001");
  }

  @Test
  void newOrderSingle_missingClOrdId_emitsBusinessReject() {
    SequenceRecoveryService svc = new SequenceRecoveryService(() -> 0L);
    RecordingSink sink = new RecordingSink();
    FixGateway gw = gateway(svc, sink);

    gw.onLogon(SESSION, 1L);
    // NOS without tag 11
    String raw =
        "8=FIX.4.4"
            + SOH
            + "35=D"
            + SOH
            + "34=1"
            + SOH
            + "49=CLIENT"
            + SOH
            + "56=EMS"
            + SOH
            + "48=BBG000BLNNH6"
            + SOH
            + "54=1"
            + SOH
            + "38=100"
            + SOH
            + "1=ACC1"
            + SOH
            + "10=0"
            + SOH;
    gw.onInbound(SESSION, raw);

    List<FixMessage> rejects = sink.ofType("j");
    assertThat(rejects).hasSize(1);
    assertThat(rejects.get(0).get(FixTags.TEXT)).contains("tag=" + FixTags.CL_ORD_ID);
  }

  @Test
  void cancelRequest_F_rejectedAsStagingOnly() {
    SequenceRecoveryService svc = new SequenceRecoveryService(() -> 0L);
    RecordingSink sink = new RecordingSink();
    FixGateway gw = gateway(svc, sink);

    gw.onLogon(SESSION, 1L);
    gw.onInbound(SESSION, control("F", 1));

    List<FixMessage> rejects = sink.ofType("j");
    assertThat(rejects).hasSize(1);
    assertThat(rejects.get(0).get(FixTags.REF_MSG_TYPE)).isEqualTo("F");
    assertThat(rejects.get(0).get(FixTags.BUSINESS_REJECT_REASON)).isEqualTo("3");
  }

  @Test
  void cancelReplace_G_rejectedAsStagingOnly() {
    SequenceRecoveryService svc = new SequenceRecoveryService(() -> 0L);
    RecordingSink sink = new RecordingSink();
    FixGateway gw = gateway(svc, sink);

    gw.onLogon(SESSION, 1L);
    gw.onInbound(SESSION, control("G", 1));

    List<FixMessage> rejects = sink.ofType("j");
    assertThat(rejects).hasSize(1);
    assertThat(rejects.get(0).get(FixTags.REF_MSG_TYPE)).isEqualTo("G");
  }

  @Test
  void unknownMsgType_emitsBusinessReject() {
    SequenceRecoveryService svc = new SequenceRecoveryService(() -> 0L);
    RecordingSink sink = new RecordingSink();
    FixGateway gw = gateway(svc, sink);

    gw.onLogon(SESSION, 1L);
    gw.onInbound(SESSION, control("Z", 1));

    assertThat(sink.ofType("j")).hasSize(1);
  }

  // ── Contract #1: reconnect calls logon AND resumeOutbound, redelivers missed ──

  @Test
  void reconnect_replaysMissedOutboundMessages() {
    SequenceRecoveryService svc = new SequenceRecoveryService(() -> 0L);
    RecordingSink sink = new RecordingSink();
    FixGateway gw = gateway(svc, sink);

    gw.onLogon(SESSION, 1L);
    gw.onInbound(SESSION, newOrderSingle(1, "CL001", "44=9950")); // outbound seq 1
    gw.onInbound(SESSION, newOrderSingle(2, "CL002", "44=9951")); // outbound seq 2
    int before = sink.sent.size();

    long nextInbound = svc.getSession(SESSION).orElseThrow().nextExpectedSeq();
    FixGateway.ReconnectOutcome outcome = gw.reconnect(SESSION, nextInbound, 1L);

    assertThat(outcome.status()).isEqualTo(FixGateway.ReconnectStatus.RESUMED);
    assertThat(outcome.replayed()).isEqualTo(2);
    // The two buffered ExecutionReports were re-delivered.
    assertThat(sink.sent.size() - before).isEqualTo(2);
  }

  // ── Contract #2: evicted hole → RESET (do not silently resume) ────────────────

  @Test
  void reconnect_evictedHole_issuesReset() {
    // Resend window of 2: after 4 sends, seqs 1 and 2 are evicted.
    SequenceRecoveryService svc = new SequenceRecoveryService(() -> 0L, 30_000L, 2);
    RecordingSink sink = new RecordingSink();
    FixGateway gw = gateway(svc, sink);

    gw.onLogon(SESSION, 1L);
    gw.onInbound(SESSION, newOrderSingle(1, "CL001", null));
    gw.onInbound(SESSION, newOrderSingle(2, "CL002", null));
    gw.onInbound(SESSION, newOrderSingle(3, "CL003", null));
    gw.onInbound(SESSION, newOrderSingle(4, "CL004", null));

    // Client asks to resume from seq 1, but 1 and 2 were evicted (lowest retained is 3).
    FixGateway.ReconnectOutcome outcome = gw.reconnect(SESSION, 5L, 1L);

    assertThat(outcome.status()).isEqualTo(FixGateway.ReconnectStatus.RESET);
    List<FixMessage> logouts = sink.ofType("5");
    assertThat(logouts).hasSize(1);
    assertThat(logouts.get(0).get(FixTags.TEXT)).contains("EMS-SES-2002");
  }

  @Test
  void reconnect_contiguousResume_isNotReset() {
    SequenceRecoveryService svc = new SequenceRecoveryService(() -> 0L, 30_000L, 2);
    RecordingSink sink = new RecordingSink();
    FixGateway gw = gateway(svc, sink);

    gw.onLogon(SESSION, 1L);
    gw.onInbound(SESSION, newOrderSingle(1, "CL001", null));
    gw.onInbound(SESSION, newOrderSingle(2, "CL002", null));

    // Lowest retained is 1 (window=2, only 2 sent); resuming from 1 is contiguous.
    FixGateway.ReconnectOutcome outcome = gw.reconnect(SESSION, 3L, 1L);
    assertThat(outcome.status()).isEqualTo(FixGateway.ReconnectStatus.RESUMED);
    assertThat(sink.ofType("5")).isEmpty();
  }

  // ── Contract #3: one TestRequest per window, cleared by inbound activity ──────

  @Test
  void heartbeat_sendsOneTestRequestPerWindow_resetByActivity() {
    long[] now = {0L};
    LongSupplier clock = () -> now[0];
    SequenceRecoveryService svc = new SequenceRecoveryService(clock, 1_000L, 100);
    RecordingSink sink = new RecordingSink();
    FixGateway gw = gateway(svc, sink);

    gw.onLogon(SESSION, 1L); // lastActivity = 0

    // Enter the [interval, 2·interval) window; poll several times.
    now[0] = 1_500L;
    gw.pollHeartbeat(SESSION);
    gw.pollHeartbeat(SESSION);
    gw.pollHeartbeat(SESSION);
    assertThat(sink.ofType("1")).hasSize(1); // exactly one TestRequest despite repeated polls

    // Peer answers (inbound heartbeat) — clears the latch and resets liveness.
    now[0] = 1_600L;
    gw.onInbound(SESSION, control("0", 1));

    // Enter the window again from the new activity baseline.
    now[0] = 2_700L; // elapsed 1100 from 1600 → SEND_TEST_REQUEST, < 2000 so not STALE
    gw.pollHeartbeat(SESSION);
    assertThat(sink.ofType("1")).hasSize(2); // one more, total two
  }

  // ── Tag 9700 trace adoption + fallback (8.3) ──────────────────────────────────

  private static final String CLIENT_TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String SESSION_TRACE = "aaaabbbbccccddddeeeeffff00001111";

  private FixGateway traceGateway(
      io.crossasset.ems.observability.trace.TracePropagator traces, RecordingSink sink) {
    return new FixGateway(
        new FakeOms(),
        new SequenceRecoveryService(() -> 0L),
        sink,
        "EMS",
        "CLIENT",
        traces,
        sessionId -> SESSION_TRACE);
  }

  @Test
  void inboundTag9700_adoptsClientTrace() {
    var traces = new io.crossasset.ems.observability.trace.TracePropagator();
    FixGateway gw = traceGateway(traces, new RecordingSink());
    gw.onLogon(SESSION, 1L);
    String hex = TraceparentTag.encode(TraceparentTag.traceparentFor(CLIENT_TRACE, "CL-TRACED"));
    gw.onInbound(SESSION, newOrderSingle(1, "CL-TRACED", "9700=" + hex));
    assertThat(traces.lookup("CL-TRACED")).contains(CLIENT_TRACE);
  }

  @Test
  void inboundWithoutTag9700_fallsBackToSessionTrace() {
    var traces = new io.crossasset.ems.observability.trace.TracePropagator();
    FixGateway gw = traceGateway(traces, new RecordingSink());
    gw.onLogon(SESSION, 1L);
    gw.onInbound(SESSION, newOrderSingle(1, "CL-PLAIN", null));
    assertThat(traces.lookup("CL-PLAIN")).contains(SESSION_TRACE);
  }

  @Test
  void inboundMalformedTag9700_fallsBackToSessionTrace() {
    var traces = new io.crossasset.ems.observability.trace.TracePropagator();
    FixGateway gw = traceGateway(traces, new RecordingSink());
    gw.onLogon(SESSION, 1L);
    gw.onInbound(SESSION, newOrderSingle(1, "CL-BADTRACE", "9700=nothex"));
    assertThat(traces.lookup("CL-BADTRACE")).contains(SESSION_TRACE);
  }
}
