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
 * MiFIR RTS 22 transaction reporting (task 12.9, [[rts-22-27-28]]): the EU/UK T+1 transaction
 * report to the NCA via an ARM. The dialect's distinguishing fields ride in {@code trade.fields()}:
 * {@code buyerLei} / {@code sellerLei} (both legs identified), {@code isin} (MiFIR identifies by
 * ISIN, not FIGI), {@code venueMic} (or XOFF/SINT off-venue), and {@code capacity} (DEAL
 * own-account / MTCH matched-principal / AOTC any-other).
 */
public final class Rts22Adapter implements RegulatorAdapter {

  private final boolean acks;

  private Rts22Adapter(boolean acks) {
    this.acks = acks;
  }

  public static Rts22Adapter acking() {
    return new Rts22Adapter(true);
  }

  public static Rts22Adapter rejecting() {
    return new Rts22Adapter(false);
  }

  @Override
  public Regulator regulator() {
    return Regulator.MIFIR_RTS22;
  }

  @Override
  public String buildPayload(ReportableTrade trade) {
    StringBuilder sb = new StringBuilder();
    sb.append("RTS22|ref=").append(trade.tradeRef());
    sb.append("|isin=").append(trade.fields().getOrDefault("isin", trade.instrumentId()));
    sb.append("|venue=").append(trade.fields().getOrDefault("venueMic", "XOFF"));
    sb.append("|side=").append(trade.side());
    sb.append("|qty=").append(trade.qty());
    sb.append("|px=").append(trade.price());
    for (var e : new TreeMap<>(trade.fields()).entrySet()) {
      if (!e.getKey().equals("isin") && !e.getKey().equals("venueMic")) {
        sb.append('|').append(e.getKey()).append('=').append(e.getValue());
      }
    }
    return sb.toString();
  }

  @Override
  public SubmitResponse submit(String payload) {
    return acks
        ? SubmitResponse.acked("RTS22-ACK-" + Integer.toHexString(payload.hashCode()))
        : SubmitResponse.nacked("RTS22-REJECT-MOCK");
  }
}
