/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.confirmation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-process mock confirmation network (stands in for MarketAxess Post-Trade for corp bond in v0).
 * Deterministic: the counterparty records and affirmation responses are pre-seeded, so submitting
 * the same trade reproduces the same match outcome — exactly what replay requires. No real wire.
 */
public final class MockConfirmationNetwork implements ConfirmationNetwork {

  private final String name;
  private final Map<String, TradeRecord> counterpartyRecords = new HashMap<>();
  private final Map<String, AffirmationResponse> affirmations = new HashMap<>();
  private AffirmationResponse defaultAffirmation = AffirmationResponse.accepted();

  public MockConfirmationNetwork(String name) {
    this.name = name;
  }

  /** Mock MarketAxess Post-Trade, the v0 corp-bond confirmation network. */
  public static MockConfirmationNetwork marketAxessPostTrade() {
    return new MockConfirmationNetwork("MARKETAXESS_POST_TRADE");
  }

  /** Seed the counterparty's posted record for a trade ref. */
  public MockConfirmationNetwork withCounterpartyRecord(TradeRecord record) {
    counterpartyRecords.put(record.tradeRef(), record);
    return this;
  }

  /** Seed an affirmation response for an allocation ref. */
  public MockConfirmationNetwork withAffirmation(
      String allocationRef, AffirmationResponse response) {
    affirmations.put(allocationRef, response);
    return this;
  }

  /** Change the response returned when no specific affirmation was seeded. */
  public MockConfirmationNetwork withDefaultAffirmation(AffirmationResponse response) {
    this.defaultAffirmation = response;
    return this;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public Optional<TradeRecord> counterpartyRecord(String tradeRef) {
    return Optional.ofNullable(counterpartyRecords.get(tradeRef));
  }

  @Override
  public AffirmationResponse affirm(String allocationRef) {
    return affirmations.getOrDefault(allocationRef, defaultAffirmation);
  }
}
