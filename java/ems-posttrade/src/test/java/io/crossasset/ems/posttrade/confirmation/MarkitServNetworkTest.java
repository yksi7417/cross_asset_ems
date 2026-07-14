/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.confirmation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class MarkitServNetworkTest {

  @Test
  void nameReturnsMarkitServ() {
    MarkitServNetwork network = new MarkitServNetwork();
    assertEquals("MARKITSERV", network.name());
  }

  @Test
  void counterpartyRecordEmptyWhenNoMapping() {
    MarkitServNetwork network = new MarkitServNetwork();
    Optional<TradeRecord> record = network.counterpartyRecord("no-such-ref");
    assertTrue(record.isEmpty());
  }

  @Test
  void allegedTradeEmptyWhenNoAllegation() {
    MarkitServNetwork network = new MarkitServNetwork();
    Optional<TradeRecord> record = network.allegedTrade("no-such-uti");
    assertTrue(record.isEmpty());
  }

  @Test
  void defaultAffirmationAccepted() {
    MarkitServNetwork network = new MarkitServNetwork();
    ConfirmationNetwork.AffirmationResponse response = network.affirm("any-ref");
    assertTrue(response.affirmed());
  }
}
