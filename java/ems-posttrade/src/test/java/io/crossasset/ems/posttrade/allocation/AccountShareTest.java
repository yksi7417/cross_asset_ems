/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AccountShareTest {

  @Test
  void constructionSetsFields() {
    AccountShare share = new AccountShare("acct-1", "pb-1", 5000L);
    assertEquals("acct-1", share.account());
    assertEquals("pb-1", share.primeBroker());
    assertEquals(5000L, share.weightBps());
  }

  @Test
  void nullAccountThrows() {
    assertThrows(NullPointerException.class, () -> new AccountShare(null, "pb", 100L));
  }

  @Test
  void negativeWeightThrows() {
    assertThrows(IllegalArgumentException.class, () -> new AccountShare("a", "pb", -1L));
  }

  @Test
  void zeroWeightAllowed() {
    AccountShare share = new AccountShare("acct", "pb", 0L);
    assertNotNull(share);
    assertEquals(0L, share.weightBps());
  }
}
