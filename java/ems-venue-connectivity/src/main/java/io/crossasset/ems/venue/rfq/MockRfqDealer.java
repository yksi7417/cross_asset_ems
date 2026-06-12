/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue.rfq;

import java.util.Optional;
import java.util.function.ToLongFunction;

/**
 * Scripted dealer for the demo edge and tests (task 11.18): quotes around a reference price (the
 * simulated feed's last) with a fixed spread, deterministic per (dealer, rfq). Behavior knobs cover
 * the negotiation paths the desktop must handle: a dealer that declines names, one that quotes then
 * fades on confirm (last look), and plain firm quoting.
 */
public final class MockRfqDealer implements RfqDealer {

  private final String dealer;
  private final ToLongFunction<String> referencePx; // figi → px (the edge wires the live feed)
  private final long spreadBp;
  private final long quoteTtlMillis;
  private final boolean declines;
  private final boolean fadesOnConfirm;

  private MockRfqDealer(
      String dealer,
      ToLongFunction<String> referencePx,
      long spreadBp,
      long quoteTtlMillis,
      boolean declines,
      boolean fadesOnConfirm) {
    this.dealer = dealer;
    this.referencePx = referencePx;
    this.spreadBp = spreadBp;
    this.quoteTtlMillis = quoteTtlMillis;
    this.declines = declines;
    this.fadesOnConfirm = fadesOnConfirm;
  }

  /** A firm dealer quoting {@code spreadBp} around the reference. */
  public static MockRfqDealer firm(
      String dealer, ToLongFunction<String> referencePx, long spreadBp, long quoteTtlMillis) {
    return new MockRfqDealer(dealer, referencePx, spreadBp, quoteTtlMillis, false, false);
  }

  /** A dealer that never quotes (no axe in the name). */
  public static MockRfqDealer declining(String dealer) {
    return new MockRfqDealer(dealer, px -> 0, 0, 0, true, false);
  }

  /** A dealer that quotes tight but fades on confirm — the last-look path. */
  public static MockRfqDealer fading(
      String dealer, ToLongFunction<String> referencePx, long spreadBp, long quoteTtlMillis) {
    return new MockRfqDealer(dealer, referencePx, spreadBp, quoteTtlMillis, false, true);
  }

  @Override
  public String dealer() {
    return dealer;
  }

  @Override
  public Optional<Rfq.QuoteResponse> quote(Rfq rfq, String responseId, long nowMillis) {
    if (declines) {
      return Optional.empty();
    }
    long reference = referencePx.applyAsLong(rfq.figi());
    // Buyer pays reference + spread, seller receives reference − spread.
    long px =
        rfq.side() == 1
            ? reference + reference * spreadBp / 10_000
            : reference - reference * spreadBp / 10_000;
    return Optional.of(
        new Rfq.QuoteResponse(
            responseId,
            rfq.rfqId(),
            dealer,
            px,
            rfq.qty(),
            fadesOnConfirm
                ? Rfq.QuoteResponse.Qualifier.LAST_LOOK
                : Rfq.QuoteResponse.Qualifier.FIRM,
            nowMillis + quoteTtlMillis));
  }

  @Override
  public boolean confirm(Rfq.QuoteResponse response, long nowMillis) {
    return !fadesOnConfirm && response.validUntilMillis() >= nowMillis;
  }
}
