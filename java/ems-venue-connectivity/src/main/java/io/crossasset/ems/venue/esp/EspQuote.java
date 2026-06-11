/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue.esp;

import java.util.Objects;

/**
 * One executable streaming quote (task 11.17): a dealer's two-sided, size-limited, time-limited
 * price. Unlike indicative market data (18.12), hitting an ESP quote is an order — subject to the
 * dealer's last look. Prices are fixed-point (4dp); {@code ttlMillis} bounds executability.
 */
public record EspQuote(
    String quoteId,
    String venueMic,
    String figi,
    long bidPx,
    long bidQty,
    long askPx,
    long askQty,
    long quotedAtMillis,
    long ttlMillis) {

  public EspQuote {
    Objects.requireNonNull(quoteId, "quoteId");
    Objects.requireNonNull(venueMic, "venueMic");
    Objects.requireNonNull(figi, "figi");
  }

  /** Still executable at {@code nowMillis}? */
  public boolean isLive(long nowMillis) {
    return nowMillis < quotedAtMillis + ttlMillis;
  }

  /** The executable price for a taker side (1 = buy hits the ask, 2 = sell hits the bid). */
  public long pxForSide(int side) {
    return side == 1 ? askPx : bidPx;
  }

  /** The executable size for a taker side. */
  public long qtyForSide(int side) {
    return side == 1 ? askQty : bidQty;
  }
}
