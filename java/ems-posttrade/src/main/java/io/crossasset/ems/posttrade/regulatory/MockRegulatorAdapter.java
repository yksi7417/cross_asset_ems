/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory;

import java.util.TreeMap;

/**
 * A generic in-process mock regulator adapter for any {@link Regulator} (FINRA CAT, CFTC SDR, …),
 * the multi-asset counterpart to {@link TraceMockAdapter}. Builds a deterministic payload and acks
 * it with no real wire, so cross-asset pipelines (Phase 16) run and replay byte-identically. Real
 * per-regulator wire dialects are post-MVP.
 */
public final class MockRegulatorAdapter implements RegulatorAdapter {

  private final Regulator regulator;
  private final boolean acks;

  private MockRegulatorAdapter(Regulator regulator, boolean acks) {
    this.regulator = regulator;
    this.acks = acks;
  }

  /** An acking mock for {@code regulator}. */
  public static MockRegulatorAdapter acking(Regulator regulator) {
    return new MockRegulatorAdapter(regulator, true);
  }

  /** A nacking mock for {@code regulator} (exercises the retry/fail path). */
  public static MockRegulatorAdapter rejecting(Regulator regulator) {
    return new MockRegulatorAdapter(regulator, false);
  }

  @Override
  public Regulator regulator() {
    return regulator;
  }

  @Override
  public String buildPayload(ReportableTrade trade) {
    StringBuilder sb = new StringBuilder();
    sb.append(regulator).append("|ref=").append(trade.tradeRef());
    sb.append("|inst=").append(trade.instrumentId());
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
      return SubmitResponse.acked(
          "ACK-" + regulator + "-" + Integer.toHexString(payload.hashCode()));
    }
    return SubmitResponse.nacked(regulator + "-REJECT-MOCK");
  }
}
