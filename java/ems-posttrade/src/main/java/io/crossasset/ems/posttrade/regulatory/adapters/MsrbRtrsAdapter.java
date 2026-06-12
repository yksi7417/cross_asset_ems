/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory.adapters;

import io.crossasset.ems.posttrade.regulatory.Regulator;
import io.crossasset.ems.posttrade.regulatory.RegulatorAdapter;
import io.crossasset.ems.posttrade.regulatory.ReportableTrade;
import java.util.TreeMap;

/**
 * MSRB RTRS submission (task 12.7, [[msrb-rtrs]]): US municipal-bond post-trade reporting —
 * 15-minute window, disseminated publicly via EMMA. The RTRS dialect's distinguishing fields ride
 * in {@code trade.fields()}: {@code cusip}, {@code datedDate}, {@code coupon}, {@code yield} (price
 * OR yield must be present), and {@code capacity} (P = principal, A = agent).
 *
 * <p>Mock-submission scope like its TRACE/CAT siblings: deterministic payload + ack, no network;
 * the real RTRS Web/message portal drops in behind the same interface.
 */
public final class MsrbRtrsAdapter implements RegulatorAdapter {

  private final boolean acks;

  private MsrbRtrsAdapter(boolean acks) {
    this.acks = acks;
  }

  public static MsrbRtrsAdapter acking() {
    return new MsrbRtrsAdapter(true);
  }

  public static MsrbRtrsAdapter rejecting() {
    return new MsrbRtrsAdapter(false);
  }

  @Override
  public Regulator regulator() {
    return Regulator.MSRB_RTRS;
  }

  @Override
  public String buildPayload(ReportableTrade trade) {
    StringBuilder sb = new StringBuilder();
    sb.append("RTRS|ref=").append(trade.tradeRef());
    sb.append("|cusip=").append(trade.fields().getOrDefault("cusip", trade.instrumentId()));
    sb.append("|side=").append(trade.side());
    sb.append("|par=").append(trade.qty()); // munis report PAR, not share qty
    sb.append("|px=").append(trade.price());
    for (var e : new TreeMap<>(trade.fields()).entrySet()) {
      if (!e.getKey().equals("cusip")) {
        sb.append('|').append(e.getKey()).append('=').append(e.getValue());
      }
    }
    return sb.toString();
  }

  @Override
  public SubmitResponse submit(String payload) {
    return acks
        ? SubmitResponse.acked("RTRS-ACK-" + Integer.toHexString(payload.hashCode()))
        : SubmitResponse.nacked("RTRS-REJECT-MOCK");
  }
}
