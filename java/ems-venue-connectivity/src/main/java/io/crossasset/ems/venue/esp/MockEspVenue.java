/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue.esp;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic in-process ESP dealer (task 11.17): tests and the demo edge drive quotes with
 * {@link #post}; execution applies a configurable last look — accept while the market (the latest
 * posted quote) hasn't moved beyond {@code lastLookToleranceBp} against the dealer, reject
 * otherwise. Hold time is simulated as a fixed value so last-look stats have something honest to
 * measure. No clock, no randomness: the caller stamps time.
 */
public final class MockEspVenue implements EspVenue {

  private record Sub(String id, String figi, EspQuoteListener listener) {}

  private final String venueMic;
  private final long lastLookToleranceBp;
  private final long simulatedHoldMillis;
  private final Map<String, Sub> subs = new LinkedHashMap<>();
  private final Map<String, EspQuote> quotesById = new LinkedHashMap<>();
  private final Map<String, EspQuote> latestByFigi = new LinkedHashMap<>();
  private final AtomicLong subSeq = new AtomicLong(1);
  private final AtomicLong quoteSeq = new AtomicLong(1);

  public MockEspVenue(String venueMic, long lastLookToleranceBp, long simulatedHoldMillis) {
    this.venueMic = Objects.requireNonNull(venueMic, "venueMic");
    this.lastLookToleranceBp = lastLookToleranceBp;
    this.simulatedHoldMillis = simulatedHoldMillis;
  }

  @Override
  public String venueMic() {
    return venueMic;
  }

  @Override
  public synchronized String subscribe(String figi, EspQuoteListener listener) {
    String id = "ESP-SUB-" + subSeq.getAndIncrement();
    subs.put(id, new Sub(id, figi, Objects.requireNonNull(listener, "listener")));
    return id;
  }

  @Override
  public synchronized boolean unsubscribe(String subscriptionId) {
    return subs.remove(subscriptionId) != null;
  }

  /** Post a fresh two-sided quote; subscribers of the pair receive it. Returns the quote. */
  public synchronized EspQuote post(
      String figi,
      long bidPx,
      long bidQty,
      long askPx,
      long askQty,
      long nowMillis,
      long ttlMillis) {
    EspQuote quote =
        new EspQuote(
            venueMic + "-Q" + quoteSeq.getAndIncrement(),
            venueMic,
            figi,
            bidPx,
            bidQty,
            askPx,
            askQty,
            nowMillis,
            ttlMillis);
    quotesById.put(quote.quoteId(), quote);
    latestByFigi.put(figi, quote);
    for (Sub sub : subs.values()) {
      if (sub.figi().equals(figi)) {
        sub.listener().onQuote(quote);
      }
    }
    return quote;
  }

  @Override
  public synchronized EspExecutionResult execute(
      String quoteId, int side, long qty, long nowMillis) {
    EspQuote quote = quotesById.get(quoteId);
    if (quote == null) {
      return new EspExecutionResult.Rejected(quoteId, RejectReason.QUOTE_UNKNOWN, "unknown quote");
    }
    if (!quote.isLive(nowMillis)) {
      return new EspExecutionResult.Rejected(
          quoteId, RejectReason.QUOTE_EXPIRED, "quote past its TTL");
    }
    if (qty > quote.qtyForSide(side)) {
      return new EspExecutionResult.Rejected(
          quoteId,
          RejectReason.QTY_UNAVAILABLE,
          "requested " + qty + " > quoted " + quote.qtyForSide(side));
    }
    // Last look: compare the hit price to the dealer's CURRENT market for the pair.
    EspQuote current = latestByFigi.get(quote.figi());
    long quotedPx = quote.pxForSide(side);
    long currentPx = current == null ? quotedPx : current.pxForSide(side);
    long movedBp = Math.abs(currentPx - quotedPx) * 10_000 / Math.max(1, quotedPx);
    boolean movedAgainstDealer = side == 1 ? currentPx > quotedPx : currentPx < quotedPx;
    if (movedAgainstDealer && movedBp > lastLookToleranceBp) {
      return new EspExecutionResult.Rejected(
          quoteId,
          RejectReason.LAST_LOOK_PRICE_MOVED,
          "market moved " + movedBp + "bp against the quote during last look");
    }
    return new EspExecutionResult.Filled(quoteId, quotedPx, qty, simulatedHoldMillis);
  }
}
