/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.confirmation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * MarkitServ integration (task 12.4, [[markitserv]]): the OTC-derivative confirmation network
 * (MarkitWire for rates/credit) — structurally different from bond/equity post-trade networks in
 * that matching is keyed by **UTI**, not trade ref, and an alleged trade arrives from the dealer
 * side for the buy-side to affirm or dispute. Mock-wire scope like its siblings: scripted
 * counterparty alleges, deterministic behavior; the real MarkitWire API drops in behind {@link
 * ConfirmationNetwork} unchanged.
 */
public final class MarkitServNetwork implements ConfirmationNetwork {

  private final Map<String, TradeRecord> allegedByUti = new HashMap<>();
  private final Map<String, String> utiByTradeRef = new HashMap<>();
  private final Map<String, AffirmationResponse> affirmations = new HashMap<>();
  private AffirmationResponse defaultAffirmation = AffirmationResponse.accepted();

  @Override
  public String name() {
    return "MARKITSERV";
  }

  /** The dealer alleges a swap (their side of the trade) under its UTI. */
  public MarkitServNetwork withAllegedTrade(String uti, TradeRecord record) {
    allegedByUti.put(Objects.requireNonNull(uti), Objects.requireNonNull(record));
    utiByTradeRef.put(record.tradeRef(), uti);
    return this;
  }

  /** Map our trade ref to the UTI the dealer alleged under (the pairing step). */
  public MarkitServNetwork withUtiMapping(String tradeRef, String uti) {
    utiByTradeRef.put(tradeRef, uti);
    return this;
  }

  public MarkitServNetwork withAffirmation(String allocationRef, AffirmationResponse response) {
    affirmations.put(allocationRef, response);
    return this;
  }

  public MarkitServNetwork withDefaultAffirmation(AffirmationResponse response) {
    this.defaultAffirmation = response;
    return this;
  }

  /** UTI-keyed: the counterparty's alleged record is found via the trade-ref→UTI pairing. */
  @Override
  public Optional<TradeRecord> counterpartyRecord(String tradeRef) {
    String uti = utiByTradeRef.get(tradeRef);
    return uti == null ? Optional.empty() : Optional.ofNullable(allegedByUti.get(uti));
  }

  /** The alleged record for a UTI directly (the dealer-side lookup). */
  public Optional<TradeRecord> allegedTrade(String uti) {
    return Optional.ofNullable(allegedByUti.get(uti));
  }

  @Override
  public AffirmationResponse affirm(String allocationRef) {
    return affirmations.getOrDefault(allocationRef, defaultAffirmation);
  }
}
