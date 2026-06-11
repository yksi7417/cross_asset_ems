/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix.sim;

import io.crossasset.ems.fix.FixMessage;
import io.crossasset.ems.fix.FixTags;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Venue-side FIX simulator (task 11.15): the acceptor counterparty for wire-level end-to-end tests,
 * sitting alongside the in-process mock (11.2). Pairs with the venue-facing gateway (8.2) for the
 * 15.2 smoke and with any FIX initiator (conformance-style manual runs via {@link
 * FixSimulatorMain}).
 *
 * <p><b>Session layer:</b> Logon handshake, Heartbeat/TestRequest echo, inbound sequence tracking
 * with gap detection (emits {@code 35=2 ResendRequest} and drops the out-of-order message), {@code
 * 35=2} handling with PossDup replay from the outbound buffer at original sequence numbers, and
 * {@code 35=4 SequenceReset} acceptance.
 *
 * <p><b>Order handling (Appendix-D-correct pending states):</b> NewOrderSingle drives the
 * configured {@link ExecutionModel} (optional PendingNew, ack, partial/full fills at the limit
 * price or the fallback mark, or submission reject). Cancel emits {@code ExecType=6 Pending Cancel}
 * then {@code 4 Canceled}; replace emits {@code E Pending Replace} then {@code 5 Replaced}, after
 * which the replacement ClOrdID is the live chain identity. Trade busts emit {@code ExecType=H}
 * referencing the busted exec and rewind CumQty. Everything is deterministic — counters, no clock —
 * so wire captures replay byte-identically.
 */
public final class FixVenueSimulator {

  /** What the venue does with a submitted order. */
  public enum Behavior {
    ACK_ONLY,
    ACK_THEN_FULL_FILL,
    ACK_THEN_PARTIAL_THEN_FULL,
    REJECT
  }

  /** Venue behavior configuration. */
  public record ExecutionModel(
      Behavior behavior,
      boolean pendingNewFirst,
      boolean acceptCancels,
      boolean acceptReplaces,
      long fallbackMarkPx) {

    public static ExecutionModel fullFill(long fallbackMarkPx) {
      return new ExecutionModel(Behavior.ACK_THEN_FULL_FILL, false, true, true, fallbackMarkPx);
    }
  }

  private static final class OrderRec {
    final String venueOrderId;
    String clOrdId;
    final String securityId;
    final int side;
    long orderQty;
    @Nullable Long px;
    long cumQty;

    OrderRec(
        String venueOrderId,
        String clOrdId,
        String securityId,
        int side,
        long orderQty,
        @Nullable Long px) {
      this.venueOrderId = venueOrderId;
      this.clOrdId = clOrdId;
      this.securityId = securityId;
      this.side = side;
      this.orderQty = orderQty;
      this.px = px;
    }

    long leaves() {
      return orderQty - cumQty;
    }
  }

  private record SentExec(String orderKey, long qty) {}

  private final String senderCompId;
  private final String targetCompId;
  private final ExecutionModel model;
  private final Consumer<String> wire;

  private long expectedInboundSeq = 1;
  private long outboundSeq = 1;
  private final TreeMap<Long, String> outboundBuffer = new TreeMap<>();

  /** Live ClOrdID → order; replaced ClOrdIDs are re-pointed along the chain. */
  private final ConcurrentHashMap<String, OrderRec> ordersByClOrdId = new ConcurrentHashMap<>();

  private final ConcurrentHashMap<String, SentExec> execs = new ConcurrentHashMap<>();
  private long orderIdSeq = 1;
  private long execIdSeq = 1;

  public FixVenueSimulator(
      String senderCompId, String targetCompId, ExecutionModel model, Consumer<String> wire) {
    this.senderCompId = Objects.requireNonNull(senderCompId, "senderCompId");
    this.targetCompId = Objects.requireNonNull(targetCompId, "targetCompId");
    this.model = Objects.requireNonNull(model, "model");
    this.wire = Objects.requireNonNull(wire, "wire");
  }

  // ── Inbound ──────────────────────────────────────────────────────────────────

  /** Process one inbound FIX message from the initiator. */
  public synchronized void onInbound(String rawFix) {
    FixMessage msg = FixMessage.parse(rawFix);
    String msgType = msg.get(FixTags.MSG_TYPE);
    if (msgType == null) {
      return;
    }
    long seq = msg.getOptional(FixTags.MSG_SEQ_NUM).map(Long::parseLong).orElse(-1L);

    // Session-control messages that manage sequencing themselves.
    switch (msgType) {
      case "A" -> {
        expectedInboundSeq = seq + 1;
        emit(
            b ->
                b.field(FixTags.MSG_TYPE, "A")
                    .field(FixTags.ENCRYPT_METHOD, 0)
                    .field(
                        FixTags.HEART_BT_INT, msg.getOptional(FixTags.HEART_BT_INT).orElse("30")));
        return;
      }
      case "2" -> {
        resend(msg);
        return;
      }
      case "4" -> {
        msg.getOptional(FixTags.NEW_SEQ_NO).ifPresent(n -> expectedInboundSeq = Long.parseLong(n));
        return;
      }
      default -> {
        // fall through to sequenced application handling
      }
    }

    if (seq > expectedInboundSeq) {
      // Gap: ask for the missing range and drop this message (initiator will resend in order).
      emit(
          b ->
              b.field(FixTags.MSG_TYPE, "2")
                  .field(FixTags.BEGIN_SEQ_NO, expectedInboundSeq)
                  .field(FixTags.END_SEQ_NO, 0));
      return;
    }
    if (seq >= 0 && seq < expectedInboundSeq && !"Y".equals(msg.get(FixTags.POSS_DUP_FLAG))) {
      return; // duplicate
    }
    if (seq == expectedInboundSeq) {
      expectedInboundSeq++;
    }

    switch (msgType) {
      case "0" -> {
        // heartbeat — liveness only
      }
      case "1" ->
          emit(
              b ->
                  b.field(FixTags.MSG_TYPE, "0")
                      .field(FixTags.TEST_REQ_ID, msg.getOptional(FixTags.TEST_REQ_ID).orElse("")));
      case "D" -> onNewOrderSingle(msg);
      case "F" -> onCancelRequest(msg);
      case "G" -> onCancelReplace(msg);
      default -> {
        // unsupported — venue silently ignores on this surface
      }
    }
  }

  // ── Order handling ───────────────────────────────────────────────────────────

  private void onNewOrderSingle(FixMessage msg) {
    String clOrdId = msg.getOptional(FixTags.CL_ORD_ID).orElse("");
    String securityId = msg.getOptional(FixTags.SECURITY_ID).orElse("");
    int side = msg.getOptional(FixTags.SIDE).map(Integer::parseInt).orElse(1);
    long qty = msg.getOptional(FixTags.ORDER_QTY).map(Long::parseLong).orElse(0L);
    Long px = msg.getOptional(FixTags.PRICE).map(Long::parseLong).orElse(null);

    if (model.behavior() == Behavior.REJECT) {
      OrderRec rec = new OrderRec("SIM-" + orderIdSeq++, clOrdId, securityId, side, qty, px);
      execReport(rec, "8", "8", 0, 0, "simulated submission reject");
      return;
    }

    OrderRec rec = new OrderRec("SIM-" + orderIdSeq++, clOrdId, securityId, side, qty, px);
    ordersByClOrdId.put(clOrdId, rec);

    if (model.pendingNewFirst()) {
      execReport(rec, "A", "A", 0, 0, null);
    }
    execReport(rec, "0", "0", 0, 0, null);

    long fillPx = px != null ? px : model.fallbackMarkPx();
    switch (model.behavior()) {
      case ACK_THEN_FULL_FILL -> fill(clOrdId, rec.leaves(), fillPx);
      case ACK_THEN_PARTIAL_THEN_FULL -> {
        long half = Math.max(1, qty / 2);
        fill(clOrdId, half, fillPx);
        fill(clOrdId, rec.leaves(), fillPx);
      }
      case ACK_ONLY -> {
        // fills are driven externally via fill()
      }
      case REJECT -> throw new IllegalStateException("unreachable");
    }
  }

  private void onCancelRequest(FixMessage msg) {
    String orig = msg.getOptional(FixTags.ORIG_CL_ORD_ID).orElse("");
    String newClOrdId = msg.getOptional(FixTags.CL_ORD_ID).orElse("");
    OrderRec rec = ordersByClOrdId.get(orig);
    if (rec == null || !model.acceptCancels()) {
      orderCancelReject(orig, newClOrdId, "1", rec == null ? 1 : 0);
      return;
    }
    // Appendix D: pending state first, then terminal; the cancel's ClOrdID rides the reports.
    String prior = rec.clOrdId;
    rec.clOrdId = newClOrdId;
    ordersByClOrdId.put(newClOrdId, rec);
    execReportWithOrig(rec, prior, "6", "6", 0, 0, null);
    execReportWithOrig(rec, prior, "4", "4", 0, 0, null);
  }

  private void onCancelReplace(FixMessage msg) {
    String orig = msg.getOptional(FixTags.ORIG_CL_ORD_ID).orElse("");
    String newClOrdId = msg.getOptional(FixTags.CL_ORD_ID).orElse("");
    OrderRec rec = ordersByClOrdId.get(orig);
    if (rec == null || !model.acceptReplaces()) {
      orderCancelReject(orig, newClOrdId, "2", rec == null ? 1 : 0);
      return;
    }
    long newQty = msg.getOptional(FixTags.ORDER_QTY).map(Long::parseLong).orElse(rec.orderQty);
    Long newPx = msg.getOptional(FixTags.PRICE).map(Long::parseLong).orElse(rec.px);
    String prior = rec.clOrdId;
    execReportWithOrig(rec, prior, "E", "E", 0, 0, null);
    rec.orderQty = newQty;
    rec.px = newPx;
    rec.clOrdId = newClOrdId;
    ordersByClOrdId.put(newClOrdId, rec);
    execReportWithOrig(rec, prior, "5", rec.cumQty > 0 ? "1" : "0", 0, 0, null);
  }

  // ── External drive hooks (ACK_ONLY mode and busts) ──────────────────────────

  /** Fill (part of) a working order; emits 39=1 or 39=2 per remaining qty. Returns the ExecID. */
  public synchronized Optional<String> fill(String clOrdId, long qty, long px) {
    OrderRec rec = ordersByClOrdId.get(clOrdId);
    if (rec == null || qty <= 0 || qty > rec.leaves()) {
      return Optional.empty();
    }
    rec.cumQty += qty;
    String execId = execReport(rec, "F", rec.leaves() == 0 ? "2" : "1", qty, px, null);
    execs.put(execId, new SentExec(rec.clOrdId, qty));
    return Optional.of(execId);
  }

  /** Bust a prior fill (ExecType=H referencing it); CumQty rewinds. */
  public synchronized Optional<String> bust(String execId) {
    SentExec busted = execs.remove(execId);
    if (busted == null) {
      return Optional.empty();
    }
    OrderRec rec = ordersByClOrdId.get(busted.orderKey());
    rec.cumQty -= busted.qty();
    String bustExecId = nextExecId();
    emit(
        b -> {
          orderFields(b, rec, "H", rec.cumQty > 0 ? "1" : "0", bustExecId);
          b.field(FixTags.EXEC_REF_ID, execId);
          b.field(FixTags.LAST_QTY, busted.qty());
        });
    return Optional.of(bustExecId);
  }

  // ── Emission ────────────────────────────────────────────────────────────────

  private String execReport(
      OrderRec rec,
      String execType,
      String ordStatus,
      long lastQty,
      long lastPx,
      @Nullable String text) {
    return execReportWithOrig(rec, null, execType, ordStatus, lastQty, lastPx, text);
  }

  private String execReportWithOrig(
      OrderRec rec,
      @Nullable String origClOrdId,
      String execType,
      String ordStatus,
      long lastQty,
      long lastPx,
      @Nullable String text) {
    String execId = nextExecId();
    emit(
        b -> {
          orderFields(b, rec, execType, ordStatus, execId);
          if (origClOrdId != null) {
            b.field(FixTags.ORIG_CL_ORD_ID, origClOrdId);
          }
          if (lastQty > 0) {
            b.field(FixTags.LAST_QTY, lastQty).field(FixTags.LAST_PX, lastPx);
          }
          if (text != null) {
            b.field(FixTags.TEXT, text);
          }
        });
    return execId;
  }

  private void orderFields(
      FixMessage.Builder b, OrderRec rec, String execType, String ordStatus, String execId) {
    b.field(FixTags.MSG_TYPE, "8")
        .field(FixTags.ORDER_ID, rec.venueOrderId)
        .field(FixTags.EXEC_ID, execId)
        .field(FixTags.CL_ORD_ID, rec.clOrdId)
        .field(FixTags.SECURITY_ID, rec.securityId)
        .field(FixTags.SIDE, rec.side)
        .field(FixTags.EXEC_TYPE, execType)
        .field(FixTags.ORD_STATUS, ordStatus)
        .field(FixTags.CUM_QTY, rec.cumQty)
        .field(FixTags.LEAVES_QTY, rec.leaves());
  }

  private void orderCancelReject(
      String origClOrdId, String clOrdId, String responseTo, int reason) {
    emit(
        b ->
            b.field(FixTags.MSG_TYPE, "9")
                .field(FixTags.ORIG_CL_ORD_ID, origClOrdId)
                .field(FixTags.CL_ORD_ID, clOrdId)
                .field(FixTags.CXL_REJ_RESPONSE_TO, responseTo)
                .field(FixTags.CXL_REJ_REASON, reason));
  }

  private void resend(FixMessage request) {
    long from = request.getOptional(FixTags.BEGIN_SEQ_NO).map(Long::parseLong).orElse(1L);
    long to = request.getOptional(FixTags.END_SEQ_NO).map(Long::parseLong).orElse(0L);
    for (Map.Entry<Long, String> entry : outboundBuffer.tailMap(from, true).entrySet()) {
      if (to != 0 && entry.getKey() > to) {
        break;
      }
      // PossDup replay at the ORIGINAL sequence number, per FIX resend semantics.
      FixMessage original = FixMessage.parse(entry.getValue());
      FixMessage.Builder replay = FixMessage.builder();
      replay.field(FixTags.MSG_TYPE, original.get(FixTags.MSG_TYPE));
      replay.field(FixTags.MSG_SEQ_NUM, entry.getKey());
      replay.field(FixTags.POSS_DUP_FLAG, "Y");
      copy(original, replay);
      wire.accept(replay.build());
    }
  }

  private static void copy(FixMessage original, FixMessage.Builder into) {
    for (int tag :
        new int[] {
          FixTags.SENDER_COMP_ID,
          FixTags.TARGET_COMP_ID,
          FixTags.ORDER_ID,
          FixTags.EXEC_ID,
          FixTags.CL_ORD_ID,
          FixTags.ORIG_CL_ORD_ID,
          FixTags.SECURITY_ID,
          FixTags.SIDE,
          FixTags.EXEC_TYPE,
          FixTags.ORD_STATUS,
          FixTags.CUM_QTY,
          FixTags.LEAVES_QTY,
          FixTags.LAST_QTY,
          FixTags.LAST_PX,
          FixTags.TEXT,
          FixTags.ENCRYPT_METHOD,
          FixTags.HEART_BT_INT,
          FixTags.TEST_REQ_ID,
          FixTags.CXL_REJ_RESPONSE_TO,
          FixTags.CXL_REJ_REASON,
          FixTags.EXEC_REF_ID,
          FixTags.BEGIN_SEQ_NO,
          FixTags.END_SEQ_NO
        }) {
      String value = original.get(tag);
      if (value != null) {
        into.field(tag, value);
      }
    }
  }

  private void emit(java.util.function.Consumer<FixMessage.Builder> body) {
    FixMessage.Builder b = FixMessage.builder();
    long seq = outboundSeq++;
    bodyWithHeader(b, seq, body);
    String raw = b.build();
    outboundBuffer.put(seq, raw);
    wire.accept(raw);
  }

  private void bodyWithHeader(
      FixMessage.Builder b, long seq, java.util.function.Consumer<FixMessage.Builder> body) {
    // MsgType first (body sets it), then header identity + seq, then the rest.
    body.accept(b);
    b.field(FixTags.MSG_SEQ_NUM, seq)
        .field(FixTags.SENDER_COMP_ID, senderCompId)
        .field(FixTags.TARGET_COMP_ID, targetCompId);
  }

  private String nextExecId() {
    return senderCompId + "-X" + execIdSeq++;
  }
}
