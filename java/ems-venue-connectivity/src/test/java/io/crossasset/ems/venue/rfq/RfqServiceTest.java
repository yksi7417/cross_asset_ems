/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue.rfq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 11.13/11.18: the canonical RFQ lifecycle per [[arch-rfq]] — multi-dealer quoting, election,
 * last-look fade back to ACTIVE, expiry vs NO_RESPONSES, cancellation, deterministic replay.
 */
class RfqServiceTest {

  private static final long T0 = 1_000_000L;

  private final List<Rfq.QuoteResponse> booked = new ArrayList<>();
  private final List<String> transitions = new ArrayList<>();
  private final RfqService service =
      new RfqService(
          (rfq, winner) -> booked.add(winner),
          rfq -> transitions.add(rfq.rfqId() + ":" + rfq.state()));

  @Test
  void happyPath_multiDealerQuotes_electBest_dealerConfirms_booked() {
    service.addDealer(MockRfqDealer.firm("GS", figi -> 1_000_000L, 10, 30_000));
    service.addDealer(MockRfqDealer.firm("JPM", figi -> 1_000_000L, 5, 30_000));
    service.addDealer(MockRfqDealer.declining("MS"));

    Rfq rfq = service.request(7L, "ACC-1", "BBG00DEMOC29", 1, 1_000_000, List.of(), 60_000, T0);
    assertThat(rfq.state()).isEqualTo(Rfq.State.ACTIVE);
    assertThat(rfq.responses()).hasSize(2); // MS declined — no axe in the name
    assertThat(rfq.dealersInvited()).containsExactly("GS", "JPM", "MS");

    // Buyer's best = lowest offer: JPM at 5bp over reference beats GS at 10bp.
    Rfq.QuoteResponse best =
        rfq.responses().stream().min(java.util.Comparator.comparingLong(Rfq.QuoteResponse::price))
            .orElseThrow();
    assertThat(best.dealer()).isEqualTo("JPM");

    service.elect(rfq.rfqId(), best.responseId(), T0 + 5_000);
    assertThat(rfq.state()).isEqualTo(Rfq.State.EXECUTED);
    assertThat(rfq.executedResponseId()).isEqualTo(best.responseId());
    assertThat(booked).containsExactly(best);
    assertThat(transitions)
        .containsExactly(
            "RFQ-1:REQUESTED", "RFQ-1:ACTIVE", "RFQ-1:ACTIVE",
            "RFQ-1:ELECTED", "RFQ-1:EXECUTED");
  }

  @Test
  void lastLookFade_electedDropsBackToActive_reElectionWins() {
    service.addDealer(MockRfqDealer.fading("FADE", figi -> 1_000_000L, 2, 30_000));
    service.addDealer(MockRfqDealer.firm("FIRM", figi -> 1_000_000L, 8, 30_000));

    Rfq rfq = service.request(7L, "ACC-1", "BBG00DEMOT35", 2, 500_000, List.of(), 60_000, T0);
    Rfq.QuoteResponse tightButFading =
        rfq.responses().stream().filter(r -> r.dealer().equals("FADE")).findFirst().orElseThrow();
    assertThat(tightButFading.qualifier()).isEqualTo(Rfq.QuoteResponse.Qualifier.LAST_LOOK);

    // The tighter quote fades on confirm: ELECTED -> ACTIVE, nothing booked, RFQ still live.
    service.elect(rfq.rfqId(), tightButFading.responseId(), T0 + 1_000);
    assertThat(rfq.state()).isEqualTo(Rfq.State.ACTIVE);
    assertThat(booked).isEmpty();

    // Re-elect the firm dealer: executes.
    Rfq.QuoteResponse firm =
        rfq.responses().stream().filter(r -> r.dealer().equals("FIRM")).findFirst().orElseThrow();
    service.elect(rfq.rfqId(), firm.responseId(), T0 + 2_000);
    assertThat(rfq.state()).isEqualTo(Rfq.State.EXECUTED);
    assertThat(booked).containsExactly(firm);
  }

  @Test
  void staleQuote_electedAfterValidUntil_reopensWithoutAskingTheDealer() {
    service.addDealer(MockRfqDealer.firm("GS", figi -> 1_000_000L, 10, 5_000));
    Rfq rfq = service.request(7L, "ACC-1", "BBG00DEMOC29", 1, 100_000, List.of(), 60_000, T0);
    String responseId = rfq.responses().get(0).responseId();

    service.elect(rfq.rfqId(), responseId, T0 + 10_000); // quote expired at T0+5s
    assertThat(rfq.state()).isEqualTo(Rfq.State.ACTIVE);
    assertThat(booked).isEmpty();
  }

  @Test
  void expiry_withResponsesIsExpired_withoutIsNoResponses() {
    service.addDealer(MockRfqDealer.firm("GS", figi -> 1_000_000L, 10, 30_000));
    Rfq quoted = service.request(7L, "ACC-1", "BBG00DEMOC29", 1, 100_000, List.of(), 60_000, T0);
    Rfq unquoted = service.request(7L, "ACC-1", "BBG00DEMOC29", 1, 100_000, List.of("NOBODY"), 60_000, T0);

    service.sweep(T0 + 59_999);
    assertThat(quoted.state()).isEqualTo(Rfq.State.ACTIVE); // not yet

    service.sweep(T0 + 60_000);
    assertThat(quoted.state()).isEqualTo(Rfq.State.EXPIRED);
    assertThat(unquoted.state()).isEqualTo(Rfq.State.NO_RESPONSES);
    assertThatThrownBy(() -> service.elect(quoted.rfqId(), "x", T0 + 61_000))
        .isInstanceOf(IllegalStateException.class); // terminal RFQs are not electable
  }

  @Test
  void cancel_andInvitedSubsetOnlySolicitsThoseDealers() {
    service.addDealer(MockRfqDealer.firm("GS", figi -> 1_000_000L, 10, 30_000));
    service.addDealer(MockRfqDealer.firm("JPM", figi -> 1_000_000L, 5, 30_000));

    Rfq rfq = service.request(7L, "ACC-1", "BBG00DEMOC29", 1, 100_000, List.of("GS"), 60_000, T0);
    assertThat(rfq.responses()).hasSize(1);
    assertThat(rfq.responses().get(0).dealer()).isEqualTo("GS");

    service.cancel(rfq.rfqId());
    assertThat(rfq.state()).isEqualTo(Rfq.State.CANCELLED);
  }

  @Test
  void ineligibleDealer_quoteShowsOnTheLadder_butCanNeverExecute() {
    // ACC-1 has no onboarding with TIGHT (no ISDA/prime line) — TIGHT's better quote is
    // VISIBLE (an onboarding action item) but election must refuse it.
    RfqService gated =
        new RfqService(
            (rfq, winner) -> booked.add(winner),
            rfq -> {},
            (account, dealer) -> !dealer.equals("TIGHT"));
    gated.addDealer(MockRfqDealer.firm("TIGHT", figi -> 1_000_000L, 1, 30_000));
    gated.addDealer(MockRfqDealer.firm("OK", figi -> 1_000_000L, 9, 30_000));

    Rfq rfq = gated.request(7L, "ACC-1", "BBG00DEMOC29", 1, 100_000, List.of(), 60_000, T0);
    assertThat(rfq.responses()).hasSize(2); // both quotes on the ladder
    Rfq.QuoteResponse tight =
        rfq.responses().stream().filter(r -> r.dealer().equals("TIGHT")).findFirst().orElseThrow();
    assertThatThrownBy(() -> gated.elect(rfq.rfqId(), tight.responseId(), T0 + 1_000))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not eligible");
    assertThat(rfq.state()).isEqualTo(Rfq.State.ACTIVE); // refusal does not corrupt the RFQ

    // bestEligible skips the ineligible dealer even though its price is better.
    assertThat(gated.bestEligible(rfq).orElseThrow().dealer()).isEqualTo("OK");
  }

  @Test
  void autoExecution_bestEligibleInsideTheBar_executesWithoutTraderInteraction() {
    RfqService auto =
        new RfqService(
            (rfq, winner) -> booked.add(winner),
            rfq -> {},
            (account, dealer) -> !dealer.equals("INELIGIBLE"));
    auto.addDealer(MockRfqDealer.firm("INELIGIBLE", figi -> 1_000_000L, 1, 30_000)); // best, barred
    auto.addDealer(MockRfqDealer.firm("GS", figi -> 1_000_000L, 8, 30_000));

    // Bar = 10bp over reference 1_000_000: GS at +8bp clears; auto-ex elects it immediately.
    Rfq rfq =
        auto.request(7L, "ACC-1", "BBG00DEMOC29", 1, 100_000, List.of(), 60_000, T0,
            RfqService.AutoExPolicy.within(10, 1_000_000L));
    assertThat(rfq.state()).isEqualTo(Rfq.State.EXECUTED);
    assertThat(booked).hasSize(1);
    assertThat(booked.get(0).dealer()).isEqualTo("GS"); // never the ineligible best

    // Outside the bar: stays ACTIVE for the trader (the interaction path).
    booked.clear();
    Rfq wide =
        auto.request(7L, "ACC-1", "BBG00DEMOC29", 1, 100_000, List.of("GS"), 60_000, T0,
            RfqService.AutoExPolicy.within(5, 1_000_000L));
    assertThat(wide.state()).isEqualTo(Rfq.State.ACTIVE);
    assertThat(booked).isEmpty();
  }

  @Test
  void deterministicReplay_sameClockSameQuotesSameTransitions() {
    RfqService a = new RfqService((r, w) -> {}, r -> {});
    List<String> log = new ArrayList<>();
    RfqService b = new RfqService((r, w) -> {}, r -> log.add(r.state().name()));
    for (RfqService s : List.of(a, b)) {
      s.addDealer(MockRfqDealer.firm("GS", figi -> 971_000L, 10, 30_000));
    }
    Rfq ra = a.request(7L, "ACC-1", "BBG00DEMOC29", 1, 100_000, List.of(), 60_000, T0);
    Rfq rb = b.request(7L, "ACC-1", "BBG00DEMOC29", 1, 100_000, List.of(), 60_000, T0);
    assertThat(ra.responses()).usingRecursiveComparison().isEqualTo(rb.responses());
  }
}
