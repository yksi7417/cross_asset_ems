/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.confirmation;

import java.util.Optional;

/**
 * A confirmation network adapter (Bloomberg VCON, MarkitSERV, DTCC CTM, MarketAxess Post-Trade, …).
 * Each handles its own wire format; the canonical {@link TradeRecord} stays the same. The MVP wires
 * an in-process mock (MarketAxess Post-Trade for corp bond); real network adapters are post-MVP.
 *
 * <p>Outbound network calls are sandboxed under replay (arch-confirmation-affirmation.md §
 * Determinism) — the adapter returns deterministic results in replay mode.
 */
public interface ConfirmationNetwork {

  /** The network name, stamped on {@link ConfirmationEvent.ConfirmationSubmitted}. */
  String name();

  /** The counterparty's posted record for {@code tradeRef}, if any has been posted. */
  Optional<TradeRecord> counterpartyRecord(String tradeRef);

  /** The counterparty's affirmation decision for an allocation breakdown. */
  AffirmationResponse affirm(String allocationRef);

  /** A network's affirmation reply: accepted, or rejected with a reason. */
  record AffirmationResponse(boolean affirmed, String reason) {
    public static AffirmationResponse accepted() {
      return new AffirmationResponse(true, null);
    }

    public static AffirmationResponse rejected(String reason) {
      return new AffirmationResponse(false, reason);
    }
  }
}
