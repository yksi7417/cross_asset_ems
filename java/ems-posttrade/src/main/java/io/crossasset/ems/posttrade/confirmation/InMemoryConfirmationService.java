/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.confirmation;

import io.crossasset.ems.posttrade.confirmation.ConfirmationEvent.AffirmationReceived;
import io.crossasset.ems.posttrade.confirmation.ConfirmationEvent.AffirmationRejected;
import io.crossasset.ems.posttrade.confirmation.ConfirmationEvent.AffirmationRequested;
import io.crossasset.ems.posttrade.confirmation.ConfirmationEvent.ConfirmationDisputed;
import io.crossasset.ems.posttrade.confirmation.ConfirmationEvent.ConfirmationMatched;
import io.crossasset.ems.posttrade.confirmation.ConfirmationEvent.ConfirmationSubmitted;
import io.crossasset.ems.posttrade.confirmation.ConfirmationEvent.ConfirmationUnmatched;
import io.crossasset.ems.posttrade.confirmation.ConfirmationEvent.ConfirmationVoided;
import io.crossasset.ems.posttrade.confirmation.ConfirmationNetwork.AffirmationResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link ConfirmationService}. Deterministic: the match outcome is a pure function of the
 * two records and the tolerance, and the network mock returns seeded results, so replay reproduces
 * the same confirmation events.
 */
public final class InMemoryConfirmationService implements ConfirmationService {

  private final Map<String, ConfirmationState> states = new ConcurrentHashMap<>();

  @Override
  public List<ConfirmationEvent> submit(
      String confirmationId,
      TradeRecord ours,
      MatchTolerance tolerance,
      ConfirmationNetwork network) {
    states.put(confirmationId, ConfirmationState.SUBMITTED);
    ConfirmationSubmitted submitted =
        new ConfirmationSubmitted(confirmationId, ours.tradeRef(), network.name());

    Optional<TradeRecord> theirs = network.counterpartyRecord(ours.tradeRef());
    if (theirs.isEmpty()) {
      states.put(confirmationId, ConfirmationState.UNMATCHED);
      return List.of(
          submitted,
          new ConfirmationUnmatched(confirmationId, "counterparty did not post", List.of()));
    }

    MatchResult result = MatchEngine.match(ours, theirs.get(), tolerance);
    if (result.matched()) {
      states.put(confirmationId, ConfirmationState.MATCHED);
      return List.of(submitted, new ConfirmationMatched(confirmationId));
    }
    states.put(confirmationId, ConfirmationState.UNMATCHED);
    return List.of(
        submitted,
        new ConfirmationUnmatched(confirmationId, "fields differ", result.differingFields()));
  }

  @Override
  public List<ConfirmationEvent> dispute(String confirmationId, String by, List<String> fields) {
    states.put(confirmationId, ConfirmationState.DISPUTED);
    return List.of(new ConfirmationDisputed(confirmationId, by, fields));
  }

  @Override
  public List<ConfirmationEvent> resolveMatched(String confirmationId) {
    states.put(confirmationId, ConfirmationState.MATCHED);
    return List.of(new ConfirmationMatched(confirmationId));
  }

  @Override
  public List<ConfirmationEvent> voidConfirmation(String confirmationId, String reason, String by) {
    states.put(confirmationId, ConfirmationState.VOIDED);
    return List.of(new ConfirmationVoided(confirmationId, reason, by));
  }

  @Override
  public List<ConfirmationEvent> requestAffirmation(
      String affirmationId, String allocationRef, ConfirmationNetwork network) {
    AffirmationRequested requested =
        new AffirmationRequested(affirmationId, allocationRef, network.name());
    AffirmationResponse response = network.affirm(allocationRef);
    if (response.affirmed()) {
      return List.of(requested, new AffirmationReceived(affirmationId));
    }
    return List.of(requested, new AffirmationRejected(affirmationId, response.reason()));
  }

  @Override
  public Optional<ConfirmationState> stateOf(String confirmationId) {
    return Optional.ofNullable(states.get(confirmationId));
  }
}
