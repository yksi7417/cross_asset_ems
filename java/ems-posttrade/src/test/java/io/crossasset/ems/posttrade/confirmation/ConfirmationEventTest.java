/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.confirmation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConfirmationEventTest {

  @Test
  void confirmationSubmittedFields() {
    ConfirmationEvent.ConfirmationSubmitted ev =
        new ConfirmationEvent.ConfirmationSubmitted("id-1", "trade-1", "network-1");
    assertEquals("id-1", ev.id());
    assertEquals("trade-1", ev.tradeRef());
    assertEquals("network-1", ev.network());
  }

  @Test
  void confirmationMatchedFields() {
    ConfirmationEvent.ConfirmationMatched ev = new ConfirmationEvent.ConfirmationMatched("id-1");
    assertEquals("id-1", ev.id());
  }

  @Test
  void confirmationUnmatchedFields() {
    ConfirmationEvent.ConfirmationUnmatched ev =
        new ConfirmationEvent.ConfirmationUnmatched("id-1", "qty differs", List.of("qty"));
    assertEquals("id-1", ev.id());
    assertEquals("qty differs", ev.reason());
    assertEquals(List.of("qty"), ev.fieldsDiffering());
  }

  @Test
  void confirmationDisputedFields() {
    ConfirmationEvent.ConfirmationDisputed ev =
        new ConfirmationEvent.ConfirmationDisputed("id-1", "party-a", List.of("price"));
    assertEquals("id-1", ev.id());
    assertEquals("party-a", ev.disputedBy());
    assertEquals(List.of("price"), ev.fields());
  }

  @Test
  void confirmationVoidedFields() {
    ConfirmationEvent.ConfirmationVoided ev =
        new ConfirmationEvent.ConfirmationVoided("id-1", "duplicate", "ops");
    assertEquals("id-1", ev.id());
    assertEquals("duplicate", ev.reason());
    assertEquals("ops", ev.by());
  }

  @Test
  void affirmationRequestedFields() {
    ConfirmationEvent.AffirmationRequested ev =
        new ConfirmationEvent.AffirmationRequested("id-1", "alloc-1", "network-1");
    assertEquals("id-1", ev.id());
    assertEquals("alloc-1", ev.allocationRef());
    assertEquals("network-1", ev.network());
  }

  @Test
  void affirmationReceivedFields() {
    ConfirmationEvent.AffirmationReceived ev = new ConfirmationEvent.AffirmationReceived("id-1");
    assertEquals("id-1", ev.id());
  }

  @Test
  void affirmationRejectedFields() {
    ConfirmationEvent.AffirmationRejected ev =
        new ConfirmationEvent.AffirmationRejected("id-1", "qty mismatch");
    assertEquals("id-1", ev.id());
    assertEquals("qty mismatch", ev.reason());
  }
}
