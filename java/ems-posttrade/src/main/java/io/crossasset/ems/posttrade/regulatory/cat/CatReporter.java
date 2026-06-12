/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory.cat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CAT order-event capture + batch submission (task 12.12, [[finra]] § CAT, sibling of the
 * trade-level adapters 12.6–12.9). Lifecycle events accumulate as they happen; {@link
 * #submitBatch} drains the buffer into one deterministic batch submission against the wire seam
 * — mirroring CAT's file-batch model (events by 8am next trading day), not per-event RPC.
 *
 * <p>v1 ships the {@link Wire#mock} wire (deterministic ack, no network), the same scoping as
 * TRACE 12.6: the would-be wire bytes are exact and replay byte-identically; the real CAT
 * Reporter Portal / SFTP wire is a drop-in {@code Wire}.
 */
public final class CatReporter {

  /** The CAT submission wire: takes the batch payload, returns ack/nack. */
  @FunctionalInterface
  public interface Wire {
    Response submit(String batchPayload);

    record Response(boolean acked, String ackRef, String errorCode) {}

    /** Deterministic in-process mock wire: acks (or nacks) every batch, ref from content. */
    static Wire mock(boolean acks) {
      return payload ->
          acks
              ? new Response(true, "CAT-ACK-" + Integer.toHexString(payload.hashCode()), null)
              : new Response(false, null, "CAT-REJECT-MOCK");
    }
  }

  /** One batch submission outcome. */
  public record BatchResult(
      String batchId, int eventCount, boolean acked, String ackRef, String errorCode) {}

  private final Wire wire;
  private final String reporterImid;
  private final List<CatEvent> buffer = new ArrayList<>();
  private final List<BatchResult> submissions = new ArrayList<>();
  private int batchSeq = 0;

  /**
   * @param reporterImid the firm's CAT reporter ID (Industry Member ID) — stamped on every batch
   */
  public CatReporter(Wire wire, String reporterImid) {
    this.wire = Objects.requireNonNull(wire, "wire");
    this.reporterImid = Objects.requireNonNull(reporterImid, "reporterImid");
  }

  // ── Lifecycle capture (call from the OMS event points) ─────────────────────────

  public CatEvent newOrder(
      String orderId, long tsMicros, String symbol, int side, long qty, Long limitPx) {
    Map<String, String> fields =
        limitPx == null
            ? Map.of("symbol", symbol, "side", String.valueOf(side), "qty", String.valueOf(qty),
                "ordType", "MKT")
            : Map.of("symbol", symbol, "side", String.valueOf(side), "qty", String.valueOf(qty),
                "ordType", "LMT", "px", String.valueOf(limitPx));
    return capture(new CatEvent(CatEvent.Type.NEW_ORDER, orderId, tsMicros, fields));
  }

  public CatEvent orderRoute(
      String orderId, long tsMicros, String routedOrderId, String destination, long qty) {
    return capture(
        new CatEvent(
            CatEvent.Type.ORDER_ROUTE,
            orderId,
            tsMicros,
            Map.of(
                "routedOrderID", routedOrderId,
                "destination", destination,
                "qty", String.valueOf(qty))));
  }

  public CatEvent orderModified(String orderId, long tsMicros, long newQty, Long newPx) {
    Map<String, String> fields =
        newPx == null
            ? Map.of("qty", String.valueOf(newQty))
            : Map.of("qty", String.valueOf(newQty), "px", String.valueOf(newPx));
    return capture(new CatEvent(CatEvent.Type.ORDER_MODIFIED, orderId, tsMicros, fields));
  }

  public CatEvent orderCanceled(String orderId, long tsMicros, long leavesQty) {
    return capture(
        new CatEvent(
            CatEvent.Type.ORDER_CANCELED,
            orderId,
            tsMicros,
            Map.of("leavesQty", String.valueOf(leavesQty))));
  }

  public CatEvent orderTrade(
      String orderId, long tsMicros, String execId, long lastQty, long lastPx, String venue) {
    return capture(
        new CatEvent(
            CatEvent.Type.ORDER_TRADE,
            orderId,
            tsMicros,
            Map.of(
                "execID", execId,
                "lastQty", String.valueOf(lastQty),
                "lastPx", String.valueOf(lastPx),
                "venue", venue)));
  }

  private CatEvent capture(CatEvent event) {
    buffer.add(event);
    return event;
  }

  // ── Batch submission ───────────────────────────────────────────────────────────

  /** Events captured and not yet submitted (the open batch), in capture order. */
  public List<CatEvent> pending() {
    return Collections.unmodifiableList(buffer);
  }

  /**
   * Drain the open batch into one submission. The batch payload is a deterministic header
   * (reporter IMID + batch seq + event count) followed by each event's payload line in capture
   * order — replay rebuilds the identical bytes. Returns the outcome (also retained, {@link
   * #submissions}). A nacked batch leaves the events in the buffer for re-submission after the
   * underlying problem is fixed — CAT errors are repair-and-resubmit by next day, not drop.
   */
  public BatchResult submitBatch() {
    String batchId = reporterImid + "-B" + ++batchSeq;
    StringBuilder sb =
        new StringBuilder("CATBATCH|imid=")
            .append(reporterImid)
            .append("|batch=")
            .append(batchId)
            .append("|events=")
            .append(buffer.size());
    for (CatEvent event : buffer) {
      sb.append('\n').append(event.toPayload());
    }
    Wire.Response response = wire.submit(sb.toString());
    BatchResult result =
        new BatchResult(
            batchId, buffer.size(), response.acked(), response.ackRef(), response.errorCode());
    if (response.acked()) {
      buffer.clear();
    }
    submissions.add(result);
    return result;
  }

  /** Every batch submission this reporter has made, in order. */
  public List<BatchResult> submissions() {
    return Collections.unmodifiableList(submissions);
  }
}
