/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.confirmation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class MockConfirmationNetworkTest {

  @Test
  void marketAxessPostTradeReturnsCorrectName() {
    MockConfirmationNetwork network = MockConfirmationNetwork.marketAxessPostTrade();
    assertEquals("MARKETAXESS_POST_TRADE", network.name());
  }

  @Test
  void counterpartyRecordEmptyWhenNotSeeded() {
    MockConfirmationNetwork network = MockConfirmationNetwork.marketAxessPostTrade();
    Optional<TradeRecord> record = network.counterpartyRecord("no-such-ref");
    assertTrue(record.isEmpty());
  }

  @Test
  void defaultAffirmationAccepted() {
    MockConfirmationNetwork network = MockConfirmationNetwork.marketAxessPostTrade();
    ConfirmationNetwork.AffirmationResponse response = network.affirm("any-ref");
    assertTrue(response.affirmed());
  }

  @Test
  void counterpartyRecordReturnedWhenSeeded() {
    MockConfirmationNetwork network = MockConfirmationNetwork.marketAxessPostTrade();
    TradeRecord record =
        new TradeRecord("t1", "CUSIP1", 1, 100, 1000, 0, "2026-01-01", "2026-01-03", "CP");
    network.withCounterpartyRecord(record);
    Optional<TradeRecord> found = network.counterpartyRecord("t1");
    assertTrue(found.isPresent());
    assertEquals("CUSIP1", found.get().instrumentId());
  }
}
