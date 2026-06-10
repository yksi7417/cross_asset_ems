/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix;

import io.crossasset.ems.oms.OrderRequest;
import io.crossasset.ems.oms.StageResult;
import io.crossasset.ems.oms.StagedOrderManager;
import io.crossasset.ems.transport.session.SequenceRecoveryService;
import io.crossasset.ems.transport.session.SequenceRecoveryService.BufferedMessage;
import io.crossasset.ems.transport.session.SequenceRecoveryService.HeartbeatAction;
import io.crossasset.ems.transport.session.SequenceRecoveryService.RecoveryResult;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client-facing FIX gateway (MVP, the "client edge").
 *
 * <p>Decodes inbound {@code 35=D NewOrderSingle} into the canonical {@code stage_orders} API
 * operation and calls the {@link StagedOrderManager}; emits {@code 35=8 ExecutionReport} on accept
 * and {@code 35=j BusinessMessageReject} on a malformed message, a validator rejection, or a
 * message type that is not allowed on a staging-only FIX session. Rides the resumable {@link
 * SequenceRecoveryService} channel (8.9) for sequencing, reconnect/resume, and heartbeat liveness.
 *
 * <p>Per arch-fix-api-bridge.md, FIX sessions are <b>staging-only</b>: {@code 35=F} (cancel),
 * {@code 35=G} (cancel/replace), and any other unsupported type are rejected. Amend/cancel over
 * FIX, venue-side FIX (8.2), and tag-9700 trace propagation (8.3) are out of scope for this task.
 *
 * <p>The gateway holds no clock of its own — all time-dependence lives in the injected {@link
 * SequenceRecoveryService}, keeping the gateway deterministic under replay.
 */
public final class FixGateway {

  // FIX MsgType values.
  static final String MT_HEARTBEAT = "0";
  static final String MT_TEST_REQUEST = "1";
  static final String MT_LOGOUT = "5";
  static final String MT_NEW_ORDER_SINGLE = "D";
  static final String MT_ORDER_CANCEL_REQUEST = "F";
  static final String MT_ORDER_CANCEL_REPLACE = "G";

  // BusinessRejectReason(380) codes we emit.
  private static final int REJ_OTHER = 0;
  private static final int REJ_UNSUPPORTED_MSG_TYPE = 3;
  private static final int REJ_CONDITIONALLY_REQUIRED_MISSING = 5;

  /** Outcome of a {@link #reconnect} attempt. */
  public enum ReconnectStatus {
    /** Inbound reconciled and any missed outbound messages were replayed. */
    RESUMED,
    /** The outbound resend buffer evicted the requested hole — session was reset. */
    RESET
  }

  /** Result of a reconnect: status, the next inbound seq the gateway expects, and replay count. */
  public record ReconnectOutcome(ReconnectStatus status, long expectedInboundSeq, int replayed) {}

  private final StagedOrderManager oms;
  private final SequenceRecoveryService sessions;
  private final OutboundSink sink;
  private final NewOrderSingleDecoder decoder = new NewOrderSingleDecoder();
  private final ExecutionReportEncoder execEncoder;
  private final BusinessMessageRejectEncoder rejectEncoder;

  private final AtomicLong execIdSeq = new AtomicLong(1);

  /** Per-session "a TestRequest is outstanding" latch (contract #3). */
  private final ConcurrentHashMap<Long, Boolean> testRequestPending = new ConcurrentHashMap<>();

  /** Per-session lock so read-next-seq → record → deliver is atomic. */
  private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

  public FixGateway(
      StagedOrderManager oms,
      SequenceRecoveryService sessions,
      OutboundSink sink,
      String senderCompId,
      String targetCompId) {
    this.oms = oms;
    this.sessions = sessions;
    this.sink = sink;
    this.execEncoder = new ExecutionReportEncoder(senderCompId, targetCompId);
    this.rejectEncoder = new BusinessMessageRejectEncoder(senderCompId, targetCompId);
  }

  private Object lockFor(long sessionId) {
    return locks.computeIfAbsent(sessionId, k -> new Object());
  }

  // ── Session establishment ──────────────────────────────────────────────────

  /** Establish (logon) a session declaring the next inbound sequence number. */
  public RecoveryResult onLogon(long sessionId, long declaredInboundSeq) {
    return sessions.logon(sessionId, declaredInboundSeq);
  }

  // ── Inbound ─────────────────────────────────────────────────────────────────

  /**
   * Process one inbound FIX message on a session. Runs sequence/gap detection first, then
   * dispatches by {@code MsgType}. Never throws on malformed input — it answers with a
   * BusinessMessageReject.
   */
  public void onInbound(long sessionId, String rawFix) {
    FixMessage msg = FixMessage.parse(rawFix);
    long incomingSeq = msg.getOptional(FixTags.MSG_SEQ_NUM).map(Long::parseLong).orElse(-1L);

    var seqCheck = sessions.checkSequence(sessionId, incomingSeq);
    if (seqCheck.isPresent()) {
      // "RESEND" (gap) or "SESSION_NOT_FOUND": do not process the application message. A real impl
      // would answer with a ResendRequest; for the MVP the gap is surfaced via session state.
      return;
    }
    // Inbound traffic = liveness; clears the heartbeat latch (contract #3).
    sessions.recordActivity(sessionId);
    testRequestPending.remove(sessionId);

    String msgType = msg.get(FixTags.MSG_TYPE);
    if (MT_NEW_ORDER_SINGLE.equals(msgType)) {
      handleNewOrderSingle(sessionId, msg);
    } else if (MT_HEARTBEAT.equals(msgType) || MT_TEST_REQUEST.equals(msgType)) {
      // Liveness already recorded above; nothing further for the MVP.
      return;
    } else if (MT_ORDER_CANCEL_REQUEST.equals(msgType) || MT_ORDER_CANCEL_REPLACE.equals(msgType)) {
      emitReject(
          sessionId,
          msgType,
          REJ_UNSUPPORTED_MSG_TYPE,
          "EMS-FIX-1002",
          "FIX session is staging-only; amend/cancel must use the API surface");
    } else {
      emitReject(
          sessionId,
          msgType == null ? "" : msgType,
          REJ_UNSUPPORTED_MSG_TYPE,
          "EMS-FIX-1001",
          "Unsupported message type");
    }
  }

  private void handleNewOrderSingle(long sessionId, FixMessage msg) {
    DecodeResult decoded = decoder.decode(msg, sessionId);
    if (decoded instanceof DecodeResult.Missing missing) {
      emitReject(
          sessionId,
          MT_NEW_ORDER_SINGLE,
          REJ_CONDITIONALLY_REQUIRED_MISSING,
          "EMS-FIX-1003",
          "Missing required field tag=" + missing.missingTag());
      return;
    }
    OrderRequest request = ((DecodeResult.Ok) decoded).request();
    StageResult result = oms.stage(request);
    if (result instanceof StageResult.Accepted accepted) {
      emitExecutionReport(sessionId, accepted.order());
    } else {
      StageResult.Rejected rejected = (StageResult.Rejected) result;
      emitReject(
          sessionId, MT_NEW_ORDER_SINGLE, REJ_OTHER, rejected.rejectCode(), rejected.message());
    }
  }

  // ── Reconnect / resume (contracts #1 and #2) ─────────────────────────────────

  /**
   * Handle a client reconnect. Per the 8.9 consumer contract this must call <b>both</b> {@code
   * logon} (inbound gap reconcile) <b>and</b> {@code resumeOutbound} (replay messages the client
   * missed) — {@code logon} alone leaves the client missing server-sent messages.
   *
   * <p>Contract #2: if {@code resumeOutbound}'s first returned sequence is greater than the
   * requested {@code clientNextExpectedOutboundSeq}, the resend buffer evicted the hole and the gap
   * is unrecoverable — the gateway issues a {@code RESET} (a {@code 35=5 Logout} carrying an {@code
   * EMS-SES} catastrophic-mismatch code) instead of silently resuming over the gap.
   */
  public ReconnectOutcome reconnect(
      long sessionId, long inboundDeclaredSeq, long clientNextExpectedOutboundSeq) {
    RecoveryResult logon = sessions.logon(sessionId, inboundDeclaredSeq);
    List<BufferedMessage> replay =
        sessions.resumeOutbound(sessionId, clientNextExpectedOutboundSeq);

    if (!replay.isEmpty() && replay.get(0).seq() > clientNextExpectedOutboundSeq) {
      // Evicted hole: the lowest retained message is already past what the client needs.
      emitReset(
          sessionId,
          "EMS-SES-2002",
          "Resend buffer evicted seq "
              + clientNextExpectedOutboundSeq
              + " (lowest retained "
              + replay.get(0).seq()
              + "); unrecoverable gap, resetting session");
      return new ReconnectOutcome(ReconnectStatus.RESET, logon.expectedSeq(), 0);
    }

    for (BufferedMessage bm : replay) {
      sink.deliver(sessionId, bm.seq(), new String(bm.payload(), StandardCharsets.US_ASCII));
    }
    return new ReconnectOutcome(ReconnectStatus.RESUMED, logon.expectedSeq(), replay.size());
  }

  // ── Heartbeats (contract #3) ─────────────────────────────────────────────────

  /**
   * Drive heartbeat liveness for a session; call once per tick. Because {@code checkLiveness} keeps
   * returning {@code SEND_TEST_REQUEST} for the whole {@code [interval, 2·interval)} window, this
   * sends exactly <b>one</b> {@code 35=1 TestRequest} on first entry into the window and then
   * waits. The latch is cleared by inbound activity (see {@link #onInbound}), so once the peer
   * answers a fresh TestRequest is sent the next time the window is entered.
   *
   * @return the action the session service reported this tick.
   */
  public HeartbeatAction pollHeartbeat(long sessionId) {
    HeartbeatAction action = sessions.checkLiveness(sessionId);
    switch (action) {
      case OK -> testRequestPending.remove(sessionId);
      case SEND_TEST_REQUEST -> {
        if (testRequestPending.putIfAbsent(sessionId, Boolean.TRUE) == null) {
          emitTestRequest(sessionId);
        }
      }
      case STALE -> {
        // Session is stale; recovery happens on the client's reconnect. No repeated emission.
      }
    }
    return action;
  }

  // ── Outbound emission ────────────────────────────────────────────────────────

  private void emitExecutionReport(long sessionId, io.crossasset.ems.oms.StagedOrder order) {
    long execId = execIdSeq.getAndIncrement();
    emit(sessionId, seq -> execEncoder.encode(order, seq, execId, null));
  }

  private void emitReject(
      long sessionId, String refMsgType, int reason, String emsCode, String text) {
    emit(sessionId, seq -> rejectEncoder.encode(seq, refMsgType, reason, emsCode, text));
  }

  private void emitTestRequest(long sessionId) {
    emit(
        sessionId,
        seq ->
            FixMessage.builder()
                .field(FixTags.MSG_TYPE, MT_TEST_REQUEST)
                .field(FixTags.MSG_SEQ_NUM, seq)
                .field(FixTags.TEST_REQ_ID, "TR-" + seq)
                .build());
  }

  private void emitReset(long sessionId, String emsCode, String text) {
    emit(
        sessionId,
        seq ->
            FixMessage.builder()
                .field(FixTags.MSG_TYPE, MT_LOGOUT)
                .field(FixTags.MSG_SEQ_NUM, seq)
                .field(FixTags.TEXT, text + " [" + emsCode + "]")
                .build());
  }

  /**
   * Encode-and-send: reserve the next outbound sequence from the session, encode the message with
   * it, record it in the resend buffer (which assigns the same sequence), and deliver it. The
   * per-session lock keeps the read-then-record atomic so the embedded {@code MsgSeqNum} matches
   * the buffer slot exactly — essential for correct resend on reconnect.
   */
  private long emit(long sessionId, java.util.function.LongFunction<String> encodeWithSeq) {
    synchronized (lockFor(sessionId)) {
      long seq =
          sessions
              .getSession(sessionId)
              .map(SequenceRecoveryService.SessionState::outboundSeq)
              .orElse(1L);
      String wire = encodeWithSeq.apply(seq);
      sessions.recordOutbound(sessionId, wire.getBytes(StandardCharsets.US_ASCII));
      sink.deliver(sessionId, seq, wire);
      return seq;
    }
  }
}
