/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue.rfq;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * RFQ orchestration (task 11.13, [[arch-rfq]] § State machine): request → dealer quotes →
 * election → dealer confirm (last look) → execution; expiry and cancellation as first-class
 * events. The service owns every transition; dealers and the execution booker are seams.
 *
 * <p>Deterministic by construction: all timing arrives as {@code nowMillis} arguments (the edge
 * passes its clock; tests pass literals), dealer quoting is synchronous on request/sweep, ids are
 * sequence-derived. {@code Elected → Active} on a failed confirm is the critical last-look path:
 * the RFQ stays live and the trader elects another response ([[arch-rfq]] note).
 */
public final class RfqService {

  /** Books the executed quote into the OMS (stage→route→fill on the edge; a list in tests). */
  @FunctionalInterface
  public interface ExecutionBooker {
    void book(Rfq rfq, Rfq.QuoteResponse winner);
  }

  private final List<RfqDealer> panel = new ArrayList<>();
  private final Map<String, Rfq> rfqs = new LinkedHashMap<>();
  private final ExecutionBooker booker;
  private final Consumer<Rfq> events;
  private int seq = 0;

  /**
   * @param booker receives the winning quote once a dealer confirms
   * @param events called after EVERY state-changing transition (the edge publishes the RFQ image
   *     to the desktop's stream topic from here)
   */
  public RfqService(ExecutionBooker booker, Consumer<Rfq> events) {
    this.booker = Objects.requireNonNull(booker, "booker");
    this.events = Objects.requireNonNull(events, "events");
  }

  /** Add a dealer to the panel future RFQs are solicited from. */
  public void addDealer(RfqDealer dealer) {
    panel.add(Objects.requireNonNull(dealer));
  }

  /**
   * Fire an RFQ to the panel (or a named subset). Dealers quote synchronously here — per-dealer
   * response latency is modeled by the dealer returning a quote whose {@code validUntilMillis}
   * encodes its own timing; the demo's mock dealers answer immediately like fast electronic FI
   * desks.
   */
  public Rfq request(
      long sessionId,
      String figi,
      int side,
      long qty,
      List<String> dealers,
      long ttlMillis,
      long nowMillis) {
    String rfqId = "RFQ-" + ++seq;
    List<String> invited =
        dealers.isEmpty() ? panel.stream().map(RfqDealer::dealer).toList() : List.copyOf(dealers);
    Rfq rfq = new Rfq(rfqId, sessionId, figi, side, qty, invited, nowMillis + ttlMillis);
    rfqs.put(rfqId, rfq);
    events.accept(rfq);
    int responseSeq = 0;
    for (RfqDealer dealer : panel) {
      if (!invited.contains(dealer.dealer())) {
        continue;
      }
      String responseId = rfqId + "-Q" + ++responseSeq;
      Optional<Rfq.QuoteResponse> quote = dealer.quote(rfq, responseId, nowMillis);
      if (quote.isPresent()) {
        rfq.addResponse(quote.get());
        events.accept(rfq);
      }
    }
    return rfq;
  }

  /**
   * Elect a response and put it to the dealer for confirmation. Confirmed ⇒ EXECUTED and the
   * booker books; faded/stale ⇒ back to ACTIVE for re-election (the last-look path).
   */
  public Rfq elect(String rfqId, String responseId, long nowMillis) {
    Rfq rfq = require(rfqId);
    if (rfq.state() != Rfq.State.ACTIVE) {
      throw new IllegalStateException(rfqId + " is " + rfq.state() + ", not electable");
    }
    Rfq.QuoteResponse response =
        rfq.responses().stream()
            .filter(r -> r.responseId().equals(responseId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("unknown response " + responseId));
    rfq.elect(responseId);
    events.accept(rfq);
    if (response.validUntilMillis() < nowMillis) {
      rfq.electionFailed(); // stale before the dealer even saw it
      events.accept(rfq);
      return rfq;
    }
    RfqDealer dealer =
        panel.stream()
            .filter(d -> d.dealer().equals(response.dealer()))
            .findFirst()
            .orElseThrow();
    if (dealer.confirm(response, nowMillis)) {
      rfq.executed(responseId);
      events.accept(rfq);
      booker.book(rfq, response);
    } else {
      rfq.electionFailed();
      events.accept(rfq);
    }
    return rfq;
  }

  public Rfq cancel(String rfqId) {
    Rfq rfq = require(rfqId);
    if (!rfq.terminal()) {
      rfq.cancel();
      events.accept(rfq);
    }
    return rfq;
  }

  /** Clock sweep: expire every live RFQ whose deadline passed (EXPIRED / NO_RESPONSES). */
  public void sweep(long nowMillis) {
    for (Rfq rfq : rfqs.values()) {
      if (!rfq.terminal()
          && rfq.state() != Rfq.State.ELECTED // an in-flight confirm finishes first
          && rfq.expireAtMillis() <= nowMillis) {
        rfq.expire();
        events.accept(rfq);
      }
    }
  }

  public Optional<Rfq> find(String rfqId) {
    return Optional.ofNullable(rfqs.get(rfqId));
  }

  private Rfq require(String rfqId) {
    Rfq rfq = rfqs.get(rfqId);
    if (rfq == null) {
      throw new IllegalArgumentException("unknown RFQ " + rfqId);
    }
    return rfq;
  }
}
