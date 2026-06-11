/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue.esp;

/**
 * The venue-side ESP contract (task 11.17): a dealer streams executable quotes and decides each
 * execution attempt under last look. Real implementations ride the venue's FIX/proprietary session
 * (11.5 EBS, 11.6 FXall …); {@link MockEspVenue} is the in-process stand-in.
 */
public interface EspVenue {

  /** Venue identity (MIC or dealer code). */
  String venueMic();

  /** Subscribe to a pair's executable stream. Returns a subscription ID. */
  String subscribe(String figi, EspQuoteListener listener);

  /** Cancel a stream subscription. */
  boolean unsubscribe(String subscriptionId);

  /**
   * Attempt to execute against a quoted price (the taker "hit"). The dealer applies last look and
   * answers — accept at the quoted price, or reject with the reason. Side uses FIX tag 54 (1 = buy
   * at ask, 2 = sell at bid).
   */
  EspExecutionResult execute(String quoteId, int side, long qty, long nowMillis);

  /** Quote callback. */
  @FunctionalInterface
  interface EspQuoteListener {
    void onQuote(EspQuote quote);
  }

  /** Last-look outcome. */
  sealed interface EspExecutionResult {
    /** Filled at the quoted price (full last-look accept). */
    record Filled(String quoteId, long px, long qty, long holdMillis)
        implements EspExecutionResult {}

    /** Rejected; {@code reason} is one of the {@link RejectReason} kinds. */
    record Rejected(String quoteId, RejectReason reason, String detail)
        implements EspExecutionResult {}
  }

  /** Why the dealer (or the quote's state) refused the hit. */
  enum RejectReason {
    QUOTE_UNKNOWN,
    QUOTE_EXPIRED,
    QTY_UNAVAILABLE,
    LAST_LOOK_PRICE_MOVED
  }
}
