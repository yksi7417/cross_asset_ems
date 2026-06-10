/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.confirmation;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.posttrade.confirmation.ConfirmationEvent.AffirmationReceived;
import io.crossasset.ems.posttrade.confirmation.ConfirmationEvent.AffirmationRejected;
import io.crossasset.ems.posttrade.confirmation.ConfirmationEvent.ConfirmationMatched;
import io.crossasset.ems.posttrade.confirmation.ConfirmationEvent.ConfirmationUnmatched;
import io.crossasset.ems.posttrade.confirmation.ConfirmationNetwork.AffirmationResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for {@link InMemoryConfirmationService}. */
class ConfirmationServiceTest {

  private static TradeRecord record(long price) {
    return new TradeRecord(
        "TR-1", "US123456AB12", 1, 100, price, 12, "2026-06-09", "2026-06-11", "CPTY-X");
  }

  @Test
  void submit_counterpartyAgrees_matches() {
    ConfirmationService svc = new InMemoryConfirmationService();
    ConfirmationNetwork net =
        MockConfirmationNetwork.marketAxessPostTrade().withCounterpartyRecord(record(9950));

    List<ConfirmationEvent> events = svc.submit("C1", record(9950), MatchTolerance.exact(), net);

    assertThat(events).hasSize(2);
    assertThat(events.get(1)).isInstanceOf(ConfirmationMatched.class);
    assertThat(svc.stateOf("C1")).contains(ConfirmationState.MATCHED);
  }

  @Test
  void submit_counterpartyDiffers_unmatchedWithFields() {
    ConfirmationService svc = new InMemoryConfirmationService();
    ConfirmationNetwork net =
        MockConfirmationNetwork.marketAxessPostTrade().withCounterpartyRecord(record(9999));

    List<ConfirmationEvent> events = svc.submit("C1", record(9950), MatchTolerance.exact(), net);

    assertThat(events.get(1))
        .isInstanceOfSatisfying(
            ConfirmationUnmatched.class, u -> assertThat(u.fieldsDiffering()).contains("price"));
    assertThat(svc.stateOf("C1")).contains(ConfirmationState.UNMATCHED);
  }

  @Test
  void submit_counterpartyDidNotPost_unmatched() {
    ConfirmationService svc = new InMemoryConfirmationService();
    ConfirmationNetwork net = MockConfirmationNetwork.marketAxessPostTrade(); // nothing seeded

    List<ConfirmationEvent> events = svc.submit("C1", record(9950), MatchTolerance.exact(), net);

    assertThat(events.get(1))
        .isInstanceOfSatisfying(
            ConfirmationUnmatched.class, u -> assertThat(u.reason()).contains("did not post"));
  }

  @Test
  void disputeThenResolve_endsMatched() {
    ConfirmationService svc = new InMemoryConfirmationService();
    ConfirmationNetwork net =
        MockConfirmationNetwork.marketAxessPostTrade().withCounterpartyRecord(record(9999));
    svc.submit("C1", record(9950), MatchTolerance.exact(), net);

    svc.dispute("C1", "ops-user", List.of("price"));
    assertThat(svc.stateOf("C1")).contains(ConfirmationState.DISPUTED);

    List<ConfirmationEvent> resolved = svc.resolveMatched("C1");
    assertThat(resolved).singleElement().isInstanceOf(ConfirmationMatched.class);
    assertThat(svc.stateOf("C1")).contains(ConfirmationState.MATCHED);
  }

  @Test
  void voidConfirmation_endsVoided() {
    ConfirmationService svc = new InMemoryConfirmationService();
    svc.voidConfirmation("C1", "trade abandoned", "ops-user");
    assertThat(svc.stateOf("C1")).contains(ConfirmationState.VOIDED);
  }

  @Test
  void affirmation_accepted_received() {
    ConfirmationService svc = new InMemoryConfirmationService();
    ConfirmationNetwork net = MockConfirmationNetwork.marketAxessPostTrade();
    List<ConfirmationEvent> events = svc.requestAffirmation("A1", "ALLOC-1", net);
    assertThat(events.get(1)).isInstanceOf(AffirmationReceived.class);
  }

  @Test
  void affirmation_rejected_capturesReason() {
    ConfirmationService svc = new InMemoryConfirmationService();
    ConfirmationNetwork net =
        MockConfirmationNetwork.marketAxessPostTrade()
            .withAffirmation("ALLOC-1", AffirmationResponse.rejected("account mismatch"));
    List<ConfirmationEvent> events = svc.requestAffirmation("A1", "ALLOC-1", net);
    assertThat(events.get(1))
        .isInstanceOfSatisfying(
            AffirmationRejected.class, r -> assertThat(r.reason()).isEqualTo("account mismatch"));
  }
}
