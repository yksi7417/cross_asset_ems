/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue.rfq;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One RFQ negotiation (tasks 11.13/11.18, [[arch-rfq]] § Canonical RFQ data model): the sender
 * solicits N dealers, time-bound quotes arrive independently, the sender ELECTS one, the dealer
 * confirms (or fails last-look and the RFQ drops back to ACTIVE for re-election). Mutable
 * aggregate owned by {@link RfqService}; every transition is clock-driven and deterministic.
 */
public final class Rfq {

  /** [[arch-rfq]] § State machine. */
  public enum State {
    REQUESTED,
    ACTIVE,
    ELECTED,
    EXECUTED,
    EXPIRED,
    CANCELLED,
    NO_RESPONSES
  }

  /** A dealer's quote. {@code validUntilMillis} drives the desktop countdown. */
  public record QuoteResponse(
      String responseId,
      String rfqId,
      String dealer,
      long price,
      long qty,
      Qualifier qualifier,
      long validUntilMillis) {

    public enum Qualifier {
      FIRM,
      INDICATIVE,
      LAST_LOOK
    }
  }

  private final String rfqId;
  private final long sessionId;
  private final String figi;
  private final int side;
  private final long qty;
  private final List<String> dealersInvited;
  private final long expireAtMillis;
  private final List<QuoteResponse> responses = new ArrayList<>();
  private State state = State.REQUESTED;
  private String electedResponseId;
  private String executedResponseId;

  Rfq(
      String rfqId,
      long sessionId,
      String figi,
      int side,
      long qty,
      List<String> dealersInvited,
      long expireAtMillis) {
    this.rfqId = Objects.requireNonNull(rfqId);
    this.sessionId = sessionId;
    this.figi = Objects.requireNonNull(figi);
    this.side = side;
    this.qty = qty;
    this.dealersInvited = List.copyOf(dealersInvited);
    this.expireAtMillis = expireAtMillis;
  }

  public String rfqId() {
    return rfqId;
  }

  public long sessionId() {
    return sessionId;
  }

  public String figi() {
    return figi;
  }

  public int side() {
    return side;
  }

  public long qty() {
    return qty;
  }

  public List<String> dealersInvited() {
    return dealersInvited;
  }

  public long expireAtMillis() {
    return expireAtMillis;
  }

  public State state() {
    return state;
  }

  public List<QuoteResponse> responses() {
    return Collections.unmodifiableList(responses);
  }

  public String electedResponseId() {
    return electedResponseId;
  }

  public String executedResponseId() {
    return executedResponseId;
  }

  // ── Transitions (package-private: RfqService owns the lifecycle) ─────────────

  void addResponse(QuoteResponse response) {
    responses.add(response);
    if (state == State.REQUESTED) {
      state = State.ACTIVE; // first dealer answer activates the negotiation
    }
  }

  void elect(String responseId) {
    electedResponseId = responseId;
    state = State.ELECTED;
  }

  /** The elected response proved un-executable (stale / last-look fail): re-open for election. */
  void electionFailed() {
    electedResponseId = null;
    state = State.ACTIVE;
  }

  void executed(String responseId) {
    executedResponseId = responseId;
    state = State.EXECUTED;
  }

  void expire() {
    state = responses.isEmpty() ? State.NO_RESPONSES : State.EXPIRED;
  }

  void cancel() {
    state = State.CANCELLED;
  }

  public boolean terminal() {
    return state == State.EXECUTED
        || state == State.EXPIRED
        || state == State.CANCELLED
        || state == State.NO_RESPONSES;
  }
}
