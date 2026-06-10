/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory;

import java.util.TreeMap;

/**
 * In-process mock TRACE (FINRA) adapter — the v0 "mock submission" for US IG corp bonds (task
 * 12.6). Builds a deterministic TRACE wire string and acks it without any real network call, so the
 * end-to-end MVP pipeline (allocation → confirmation → TRACE-mock) runs and replays
 * byte-identically. Real TRAQS / FIX-TRACE submission is post-MVP.
 */
public final class TraceMockAdapter implements RegulatorAdapter {

  private final boolean acks;

  private TraceMockAdapter(boolean acks) {
    this.acks = acks;
  }

  /** The normal mock: every submission acks. */
  public static TraceMockAdapter acking() {
    return new TraceMockAdapter(true);
  }

  /** A mock that nacks every submission, to exercise the retry/fail path. */
  public static TraceMockAdapter rejecting() {
    return new TraceMockAdapter(false);
  }

  @Override
  public Regulator regulator() {
    return Regulator.TRACE;
  }

  @Override
  public String buildPayload(ReportableTrade trade) {
    // Deterministic FIX-TRACE-dialect-shaped string: core fields then required fields in key order.
    StringBuilder sb = new StringBuilder();
    sb.append("TRACE|ref=").append(trade.tradeRef());
    sb.append("|cusip=").append(trade.instrumentId());
    sb.append("|side=").append(trade.side());
    sb.append("|qty=").append(trade.qty());
    sb.append("|px=").append(trade.price());
    for (var e : new TreeMap<>(trade.fields()).entrySet()) {
      sb.append('|').append(e.getKey()).append('=').append(e.getValue());
    }
    return sb.toString();
  }

  @Override
  public SubmitResponse submit(String payload) {
    if (acks) {
      // Deterministic ack ref derived from the payload content.
      return SubmitResponse.acked("TRACE-ACK-" + Integer.toHexString(payload.hashCode()));
    }
    return SubmitResponse.nacked("TRACE-REJECT-MOCK");
  }
}
