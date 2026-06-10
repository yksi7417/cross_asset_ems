/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.confirmation;

import java.util.Objects;

/**
 * The canonical trade record both sides post for matching (per arch-confirmation-affirmation.md).
 * Per-network adapters normalize their wire format into this envelope; the match engine compares
 * two of these. Fields are the union of the per-asset-class match keys; corp bond (the MVP asset
 * class) matches on CUSIP/instrument, side, qty, price (within ½ tick), trade date, settle date,
 * accrued.
 */
public record TradeRecord(
    String tradeRef,
    String instrumentId,
    int side,
    long qty,
    long price,
    long accrued,
    String tradeDate,
    String settleDate,
    String counterparty) {

  public TradeRecord {
    Objects.requireNonNull(tradeRef, "tradeRef");
    Objects.requireNonNull(instrumentId, "instrumentId");
  }
}
