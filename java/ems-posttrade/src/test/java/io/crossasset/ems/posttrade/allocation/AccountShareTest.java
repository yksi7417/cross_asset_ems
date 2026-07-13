/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AccountShareTest {

  @Test
  void storesAllFields() {
    var share = new AccountShare("acct-1", "PB-A", 2_500L);
    assertEquals("acct-1", share.account());
    assertEquals("PB-A", share.primeBroker());
    assertEquals(2_500L, share.weightBps());
  }

  @Test
  void nullAccountRejected() {
    assertThrows(NullPointerException.class, () -> new AccountShare(null, "PB-A", 100L));
  }

  @Test
  void negativeWeightRejected() {
    assertThrows(IllegalArgumentException.class, () -> new AccountShare("acct-1", "PB-A", -1L));
  }

  @Test
  void zeroWeightAllowed() {
    assertEquals(0L, new AccountShare("acct-1", "PB-A", 0L).weightBps());
  }

  @Test
  void blankPrimeBrokerAllowedForPbAgnosticTemplates() {
    assertEquals("", new AccountShare("acct-1", "", 100L).primeBroker());
  }

  @Test
  void valueEquality() {
    assertEquals(new AccountShare("a", "pb", 5_000L), new AccountShare("a", "pb", 5_000L));
    assertNotEquals(new AccountShare("a", "pb", 5_000L), new AccountShare("a", "pb", 5_001L));
  }
}
