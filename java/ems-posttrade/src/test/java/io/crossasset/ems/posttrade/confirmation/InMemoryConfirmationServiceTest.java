/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.confirmation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InMemoryConfirmationServiceTest {

  @Test
  void stateOfNonexistentReturnsEmpty() {
    InMemoryConfirmationService service = new InMemoryConfirmationService();
    Optional<ConfirmationState> state = service.stateOf("no-such-id");
    assertTrue(state.isEmpty());
  }

  @Test
  void disputeReturnsDisputedEvent() {
    InMemoryConfirmationService service = new InMemoryConfirmationService();
    List<ConfirmationEvent> events = service.dispute("c1", "alice", List.of("price"));
    assertEquals(1, events.size());
    assertInstanceOf(ConfirmationEvent.ConfirmationDisputed.class, events.get(0));
  }

  @Test
  void voidConfirmationReturnsVoidedEvent() {
    InMemoryConfirmationService service = new InMemoryConfirmationService();
    List<ConfirmationEvent> events = service.voidConfirmation("c1", "wrong trade", "bob");
    assertEquals(1, events.size());
    assertInstanceOf(ConfirmationEvent.ConfirmationVoided.class, events.get(0));
  }

  @Test
  void resolveMatchedReturnsMatchedEvent() {
    InMemoryConfirmationService service = new InMemoryConfirmationService();
    List<ConfirmationEvent> events = service.resolveMatched("c1");
    assertEquals(1, events.size());
    assertInstanceOf(ConfirmationEvent.ConfirmationMatched.class, events.get(0));
  }

  @Test
  void submitWithNoCounterpartyReturnsUnmatched() {
    InMemoryConfirmationService service = new InMemoryConfirmationService();
    MockConfirmationNetwork network = MockConfirmationNetwork.marketAxessPostTrade();
    TradeRecord record =
        new TradeRecord("t1", "CUSIP1", 1, 100, 1000, 0, "2026-01-01", "2026-01-03", "CP");
    List<ConfirmationEvent> events = service.submit("c1", record, MatchTolerance.exact(), network);
    assertEquals(2, events.size());
    assertInstanceOf(ConfirmationEvent.ConfirmationSubmitted.class, events.get(0));
    assertInstanceOf(ConfirmationEvent.ConfirmationUnmatched.class, events.get(1));
    assertEquals(ConfirmationState.UNMATCHED, service.stateOf("c1").orElseThrow());
  }
}
