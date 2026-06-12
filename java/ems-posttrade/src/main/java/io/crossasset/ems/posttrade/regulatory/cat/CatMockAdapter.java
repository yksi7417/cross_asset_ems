/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory.cat;

import io.crossasset.ems.posttrade.regulatory.Regulator;
import io.crossasset.ems.posttrade.regulatory.RegulatorAdapter;
import io.crossasset.ems.posttrade.regulatory.ReportableTrade;
import java.util.Map;

/**
 * CAT execution-event adapter for the 12.5 reporting pipeline (task 12.12): when the STP chain
 * reports an equity/options execution, it lands in CAT as an ORDER_TRADE (MEOT) event — the
 * {@code RegulatorDeterminer.crossAssetUs()} rules already route US_EQUITY/PREFERRED here. The
 * full order lifecycle (new/route/modify/cancel) is captured by {@link CatReporter} at the OMS
 * event points; this adapter covers the trade leg that flows with allocation/confirmation.
 *
 * <p>Mock-submission scope, like TRACE 12.6: deterministic payload + ack, no network; the real
 * CAT wire drops into {@link CatReporter.Wire} and this adapter unchanged.
 */
public final class CatMockAdapter implements RegulatorAdapter {

  private final boolean acks;

  private CatMockAdapter(boolean acks) {
    this.acks = acks;
  }

  public static CatMockAdapter acking() {
    return new CatMockAdapter(true);
  }

  public static CatMockAdapter rejecting() {
    return new CatMockAdapter(false);
  }

  @Override
  public Regulator regulator() {
    return Regulator.FINRA_CAT;
  }

  @Override
  public String buildPayload(ReportableTrade trade) {
    Map<String, String> fields = trade.fields();
    CatEvent event =
        new CatEvent(
            CatEvent.Type.ORDER_TRADE,
            // CAT links on the originating order, not the trade ref; fall back when the STP
            // context carries no order linkage (e.g. unit tests feeding bare trades).
            fields.getOrDefault("orderId", trade.tradeRef()),
            Long.parseLong(fields.getOrDefault("eventTs", "0")),
            Map.of(
                "symbol", trade.instrumentId(),
                "side", String.valueOf(trade.side()),
                "lastQty", String.valueOf(trade.qty()),
                "lastPx", String.valueOf(trade.price()),
                "tradeRef", trade.tradeRef()));
    return event.toPayload();
  }

  @Override
  public SubmitResponse submit(String payload) {
    return acks
        ? SubmitResponse.acked("CAT-ACK-" + Integer.toHexString(payload.hashCode()))
        : SubmitResponse.nacked("CAT-REJECT-MOCK");
  }
}
