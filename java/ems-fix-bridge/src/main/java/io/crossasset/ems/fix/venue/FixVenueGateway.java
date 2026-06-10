/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix.venue;

import io.crossasset.ems.fix.FixMessage;
import io.crossasset.ems.fix.FixTags;
import io.crossasset.ems.fix.OutboundSink;
import io.crossasset.ems.transport.session.SequenceRecoveryService;
import io.crossasset.ems.transport.session.SequenceRecoveryService.HeartbeatAction;
import io.crossasset.ems.venue.AbstractVenueAdapter;
import io.crossasset.ems.venue.Capability;
import io.crossasset.ems.venue.VenueEventSink;
import io.crossasset.ems.venue.VenueRef;
import io.crossasset.ems.venue.VenueRouteRequest;
import io.crossasset.ems.venue.VenueState;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;

/**
 * Venue-facing FIX gateway (task 8.2): a {@link io.crossasset.ems.venue.VenueAdapter} that speaks
 * real wire FIX. Outbound, it encodes router submissions as {@code 35=D} (plus {@code 35=F} cancel,
 * {@code 35=G} replace) over an initiator session; inbound, it decodes the venue's {@code 35=8
 * ExecutionReport} / {@code 35=9 OrderCancelReject} streams and surfaces them through the {@link
 * VenueEventSink}, correlating on ClOrdID.
 *
 * <p>Session mechanics (sequencing, resend buffer, heartbeat liveness) ride the same {@link
 * SequenceRecoveryService} the client edge uses (8.9) — one session layer, both surfaces, per
 * arch-fix-api-bridge.md. The gateway holds no clock; determinism under replay matches {@link
 * io.crossasset.ems.fix.FixGateway}. Pairs with the venue-side FIX simulator (task 11.15) for
 * wire-level end-to-end tests (15.2); the in-process mock (11.2) stays for fast paths.
 *
 * <p>FIGIs travel in {@code SecurityID(48)} bare, matching the client surface convention. Gap
 * handling on inbound mirrors the client gateway MVP: the application message is not processed and
 * the gap is surfaced via session state (a ResendRequest flow arrives with the simulator task).
 */
public final class FixVenueGateway extends AbstractVenueAdapter {

  static final String MT_HEARTBEAT = "0";
  static final String MT_TEST_REQUEST = "1";
  static final String MT_LOGON = "A";
  static final String MT_LOGOUT = "5";
  static final String MT_NEW_ORDER_SINGLE = "D";
  static final String MT_CANCEL_REQUEST = "F";
  static final String MT_CANCEL_REPLACE = "G";
  static final String MT_EXECUTION_REPORT = "8";
  static final String MT_ORDER_CANCEL_REJECT = "9";

  private final SequenceRecoveryService sessions;
  private final long venueSessionId;
  private final OutboundSink wire;
  private final String senderCompId;
  private final String targetCompId;
  private final int heartBtIntSeconds;
  private final io.crossasset.ems.observability.trace.TracePropagator traces;
  private final boolean traceAware;

  /** ClOrdID (current or historical) → routeId. */
  private final ConcurrentHashMap<String, String> clOrdToRoute = new ConcurrentHashMap<>();

  /** routeId → ClOrdID currently live at the venue. */
  private final ConcurrentHashMap<String, String> routeToClOrd = new ConcurrentHashMap<>();

  private final AtomicLong cancelSeq = new AtomicLong(1);
  private final AtomicBoolean testRequestPending = new AtomicBoolean(false);
  private final Object emitLock = new Object();

  public FixVenueGateway(
      VenueRef venueRef,
      Set<Capability> capabilities,
      VenueEventSink sink,
      boolean shadow,
      SequenceRecoveryService sessions,
      long venueSessionId,
      OutboundSink wire,
      String senderCompId,
      String targetCompId,
      int heartBtIntSeconds) {
    this(
        venueRef,
        capabilities,
        sink,
        shadow,
        sessions,
        venueSessionId,
        wire,
        senderCompId,
        targetCompId,
        heartBtIntSeconds,
        new io.crossasset.ems.observability.trace.TracePropagator(),
        false);
  }

  /**
   * Trace-aware constructor (task 8.3). When {@code traceAware} is true the venue is on the firm's
   * trace allowlist and outbound {@code 35=D} carries {@code 9700 TraceparentHex}; otherwise the
   * tag is withheld and trace continuity relies on the {@code traces} rejoin map (ClOrdID-keyed),
   * which is maintained either way — including across cancel/replace ClOrdID transitions.
   */
  public FixVenueGateway(
      VenueRef venueRef,
      Set<Capability> capabilities,
      VenueEventSink sink,
      boolean shadow,
      SequenceRecoveryService sessions,
      long venueSessionId,
      OutboundSink wire,
      String senderCompId,
      String targetCompId,
      int heartBtIntSeconds,
      io.crossasset.ems.observability.trace.TracePropagator traces,
      boolean traceAware) {
    super(venueRef, capabilities, sink, shadow);
    this.sessions = sessions;
    this.venueSessionId = venueSessionId;
    this.wire = wire;
    this.senderCompId = senderCompId;
    this.targetCompId = targetCompId;
    this.heartBtIntSeconds = heartBtIntSeconds;
    this.traces = traces;
    this.traceAware = traceAware;
  }

  // ── Session ─────────────────────────────────────────────────────────────────

  /**
   * Initiate the venue session: register it with the session layer (declaring the next inbound
   * sequence expected from the venue) and send {@code 35=A Logon}.
   */
  public void connect(long declaredInboundSeq) {
    sessions.logon(venueSessionId, declaredInboundSeq);
    if (!isShadow()) {
      emit(
          seq ->
              header(MT_LOGON, seq)
                  .field(FixTags.ENCRYPT_METHOD, 0)
                  .field(FixTags.HEART_BT_INT, heartBtIntSeconds)
                  .build());
    }
    setState(VenueState.CONNECTED);
  }

  /**
   * Drive heartbeat liveness; call once per tick. Sends exactly one {@code 35=1 TestRequest} per
   * silence window (latch cleared by inbound traffic), mirroring the client gateway's contract.
   */
  public HeartbeatAction pollHeartbeat() {
    HeartbeatAction action = sessions.checkLiveness(venueSessionId);
    switch (action) {
      case OK -> testRequestPending.set(false);
      case SEND_TEST_REQUEST -> {
        if (testRequestPending.compareAndSet(false, true)) {
          emit(seq -> header(MT_TEST_REQUEST, seq).field(FixTags.TEST_REQ_ID, "TR-" + seq).build());
        }
      }
      case STALE -> setState(VenueState.RECONNECTING);
    }
    return action;
  }

  // ── Outbound (router → venue) ───────────────────────────────────────────────

  @Override
  public void submit(VenueRouteRequest request) {
    if (isShadow()) {
      return;
    }
    clOrdToRoute.put(request.clOrdId(), request.routeId());
    routeToClOrd.put(request.routeId(), request.clOrdId());
    emit(
        seq -> {
          FixMessage.Builder b =
              header(MT_NEW_ORDER_SINGLE, seq)
                  .field(FixTags.CL_ORD_ID, request.clOrdId())
                  .field(FixTags.SECURITY_ID, request.instrumentId())
                  .field(FixTags.SIDE, request.side())
                  .field(FixTags.ORDER_QTY, request.qty());
          if (request.isMarket()) {
            b.field(FixTags.ORD_TYPE, 1);
          } else {
            b.field(FixTags.ORD_TYPE, 2).field(FixTags.PRICE, request.price());
          }
          if (traceAware) {
            traces
                .lookup(request.clOrdId())
                .ifPresent(
                    traceId ->
                        b.field(
                            io.crossasset.ems.fix.TraceparentTag.TAG,
                            io.crossasset.ems.fix.TraceparentTag.encode(
                                io.crossasset.ems.fix.TraceparentTag.traceparentFor(
                                    traceId, request.clOrdId()))));
          }
          return b.build();
        });
  }

  @Override
  public void cancel(String routeId) {
    if (isShadow()) {
      return;
    }
    String current = routeToClOrd.get(routeId);
    if (current == null) {
      return; // unknown route — nothing at the venue to cancel
    }
    String cancelClOrdId = current + ".C" + cancelSeq.getAndIncrement();
    clOrdToRoute.put(cancelClOrdId, routeId);
    traces.alias(cancelClOrdId, current);
    emit(
        seq ->
            header(MT_CANCEL_REQUEST, seq)
                .field(FixTags.ORIG_CL_ORD_ID, current)
                .field(FixTags.CL_ORD_ID, cancelClOrdId)
                .build());
  }

  @Override
  public void replace(String routeId, String newClOrdId, long newQty, @Nullable Long newPrice) {
    if (isShadow()) {
      return;
    }
    String current = routeToClOrd.get(routeId);
    if (current == null) {
      return;
    }
    clOrdToRoute.put(newClOrdId, routeId);
    traces.alias(newClOrdId, current);
    emit(
        seq -> {
          FixMessage.Builder b =
              header(MT_CANCEL_REPLACE, seq)
                  .field(FixTags.ORIG_CL_ORD_ID, current)
                  .field(FixTags.CL_ORD_ID, newClOrdId)
                  .field(FixTags.ORDER_QTY, newQty);
          if (newPrice != null) {
            b.field(FixTags.ORD_TYPE, 2).field(FixTags.PRICE, newPrice);
          } else {
            b.field(FixTags.ORD_TYPE, 1);
          }
          return b.build();
        });
  }

  // ── Inbound (venue → router) ────────────────────────────────────────────────

  /**
   * Process one inbound FIX message from the venue. Sequence/gap check first; then session messages
   * are handled locally and application messages are mapped onto the {@link VenueEventSink}.
   * Unknown correlation IDs are ignored (the venue may echo flows this gateway did not originate).
   */
  public void onInbound(String rawFix) {
    FixMessage msg = FixMessage.parse(rawFix);
    long incomingSeq = msg.getOptional(FixTags.MSG_SEQ_NUM).map(Long::parseLong).orElse(-1L);
    if (sessions.checkSequence(venueSessionId, incomingSeq).isPresent()) {
      return; // gap or unknown session — surfaced via session state (MVP, as on the client edge)
    }
    sessions.recordActivity(venueSessionId);
    testRequestPending.set(false);

    String msgType = msg.get(FixTags.MSG_TYPE);
    if (msgType == null) {
      return;
    }
    switch (msgType) {
      case MT_EXECUTION_REPORT -> onExecutionReport(msg);
      case MT_ORDER_CANCEL_REJECT -> onOrderCancelReject(msg);
      case MT_TEST_REQUEST ->
          emit(
              seq ->
                  header(MT_HEARTBEAT, seq)
                      .field(FixTags.TEST_REQ_ID, msg.getOptional(FixTags.TEST_REQ_ID).orElse(""))
                      .build());
      case MT_HEARTBEAT -> {
        // liveness already recorded
      }
      case MT_LOGON -> setState(VenueState.CONNECTED);
      case MT_LOGOUT -> setState(VenueState.RECONNECTING);
      default -> {
        // Unsupported venue message — ignore on this surface.
      }
    }
  }

  private void onExecutionReport(FixMessage msg) {
    String routeId = resolveRouteId(msg);
    if (routeId == null) {
      return;
    }
    String execType = msg.getOptional(FixTags.EXEC_TYPE).orElse("");
    switch (execType) {
      case "0" -> sink().acknowledged(routeId);
      case "A" -> sink().pendingNew(routeId);
      case "8" -> sink().rejected(routeId, msg.getOptional(FixTags.TEXT).orElse(""));
      case "4" -> sink().canceled(routeId);
      case "5" -> {
        // Venue confirmed the replace; the replacement ClOrdID becomes current.
        msg.getOptional(FixTags.CL_ORD_ID).ifPresent(cl -> routeToClOrd.put(routeId, cl));
        sink().replaced(routeId);
      }
      case "F" -> {
        long lastQty = msg.getOptional(FixTags.LAST_QTY).map(Long::parseLong).orElse(0L);
        long lastPx = msg.getOptional(FixTags.LAST_PX).map(Long::parseLong).orElse(0L);
        String execId = msg.getOptional(FixTags.EXEC_ID).orElse("");
        if ("2".equals(msg.get(FixTags.ORD_STATUS))) {
          sink().filled(routeId, lastQty, lastPx, execId);
        } else {
          sink().partialFill(routeId, lastQty, lastPx, execId);
        }
      }
      default -> {
        // ExecTypes outside the v1 surface (expired, suspended, …) — ignore.
      }
    }
  }

  private void onOrderCancelReject(FixMessage msg) {
    String routeId = resolveRouteId(msg);
    if (routeId == null) {
      return;
    }
    int reason = msg.getOptional(FixTags.CXL_REJ_REASON).map(Integer::parseInt).orElse(0);
    String responseTo = msg.getOptional(FixTags.CXL_REJ_RESPONSE_TO).orElse("1");
    if ("2".equals(responseTo)) {
      sink().replaceRejected(routeId, reason);
    } else {
      sink().cancelRejected(routeId, reason);
    }
  }

  private @Nullable String resolveRouteId(FixMessage msg) {
    String byClOrd = msg.getOptional(FixTags.CL_ORD_ID).map(clOrdToRoute::get).orElse(null);
    if (byClOrd != null) {
      return byClOrd;
    }
    return msg.getOptional(FixTags.ORIG_CL_ORD_ID).map(clOrdToRoute::get).orElse(null);
  }

  // ── Emission ────────────────────────────────────────────────────────────────

  private FixMessage.Builder header(String msgType, long seq) {
    return FixMessage.builder()
        .field(FixTags.MSG_TYPE, msgType)
        .field(FixTags.MSG_SEQ_NUM, seq)
        .field(FixTags.SENDER_COMP_ID, senderCompId)
        .field(FixTags.TARGET_COMP_ID, targetCompId);
  }

  /**
   * Encode-and-send with the same atomic read-seq → record → deliver discipline as the client
   * gateway, so the embedded MsgSeqNum always matches the resend-buffer slot.
   */
  private void emit(java.util.function.LongFunction<String> encodeWithSeq) {
    synchronized (emitLock) {
      long seq =
          sessions
              .getSession(venueSessionId)
              .map(SequenceRecoveryService.SessionState::outboundSeq)
              .orElse(1L);
      String rawFix = encodeWithSeq.apply(seq);
      sessions.recordOutbound(venueSessionId, rawFix.getBytes(StandardCharsets.US_ASCII));
      wire.deliver(venueSessionId, seq, rawFix);
    }
  }
}
