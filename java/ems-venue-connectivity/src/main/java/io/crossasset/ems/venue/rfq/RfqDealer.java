/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue.rfq;

import java.util.Optional;

/**
 * The dealer seam (task 11.13, [[arch-rfq]] § Pluggable venue adapters): one quoting
 * counterparty on an RFQ panel. Production implementations bridge to RFQ venues
 * (MarketAxess/Tradeweb-style); {@link MockRfqDealer} quotes around a reference price for the
 * demo and tests. Both halves are clock-driven (no wall-clock reads) so negotiations replay.
 */
public interface RfqDealer {

  /** Dealer code shown on the quote ladder. */
  String dealer();

  /**
   * Asked to quote. Returns the dealer's response, or empty when it declines to quote (axes,
   * inventory, no interest in the name).
   */
  Optional<Rfq.QuoteResponse> quote(Rfq rfq, String responseId, long nowMillis);

  /**
   * The sender elected this dealer's quote — the dealer's final word (last look). True = trade
   * confirmed at the quoted price; false = the quote faded and the RFQ re-opens for election.
   */
  boolean confirm(Rfq.QuoteResponse response, long nowMillis);
}
