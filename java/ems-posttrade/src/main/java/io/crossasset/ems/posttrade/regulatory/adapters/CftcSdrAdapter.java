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
 * CFTC SDR submission (task 12.8, [[cftc-sdr]]): Dodd-Frank Title VII swap reporting to a
 * registered Swap Data Repository. The dialect's distinguishing identity is the {@code uti} (Unique
 * Transaction Identifier — DERIVED DETERMINISTICALLY here from the reporting LEI + trade ref when
 * not supplied, never random: replay must rebuild the identical UTI) plus both counterparty LEIs
 * ({@code reportingLei}, {@code otherLei}) and the swap economics ({@code effectiveDate}, {@code
 * maturityDate}, notional = qty).
 */
public final class CftcSdrAdapter implements RegulatorAdapter {

  private final boolean acks;

  private CftcSdrAdapter(boolean acks) {
    this.acks = acks;
  }

  public static CftcSdrAdapter acking() {
    return new CftcSdrAdapter(true);
  }

  public static CftcSdrAdapter rejecting() {
    return new CftcSdrAdapter(false);
  }

  @Override
  public Regulator regulator() {
    return Regulator.CFTC_SDR;
  }

  /** The deterministic UTI for a trade: supplied, or derived from reporting LEI + trade ref. */
  public static String uti(ReportableTrade trade) {
    String supplied = trade.fields().get("uti");
    if (supplied != null && !supplied.isBlank()) {
      return supplied;
    }
    String lei = trade.fields().getOrDefault("reportingLei", "UNKNOWNLEI");
    return lei + "-" + Integer.toHexString((lei + "|" + trade.tradeRef()).hashCode()).toUpperCase();
  }

  @Override
  public String buildPayload(ReportableTrade trade) {
    StringBuilder sb = new StringBuilder();
    sb.append("SDR|uti=").append(uti(trade));
    sb.append("|ref=").append(trade.tradeRef());
    sb.append("|assetClass=").append(trade.assetClass());
    sb.append("|side=").append(trade.side());
    sb.append("|notional=").append(trade.qty());
    sb.append("|px=").append(trade.price());
    for (var e : new TreeMap<>(trade.fields()).entrySet()) {
      if (!e.getKey().equals("uti")) {
        sb.append('|').append(e.getKey()).append('=').append(e.getValue());
      }
    }
    return sb.toString();
  }

  @Override
  public SubmitResponse submit(String payload) {
    return acks
        ? SubmitResponse.acked("SDR-ACK-" + Integer.toHexString(payload.hashCode()))
        : SubmitResponse.nacked("SDR-REJECT-MOCK");
  }
}
