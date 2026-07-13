/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AllocationTemplateTest {

  @Test
  void ofCreatesNonDeferredTemplate() {
    List<AccountShare> shares = List.of(new AccountShare("acct-1", "pb-1", 5000L));
    AllocationTemplate tmpl =
        AllocationTemplate.of(
            "tmpl-1", 1L, AllocationPolicy.PRO_RATA, RoundingPolicy.ROUND_HALF_UP, shares);
    assertEquals("tmpl-1", tmpl.templateId());
    assertEquals(1L, tmpl.version());
    assertEquals(AllocationPolicy.PRO_RATA, tmpl.policy());
    assertEquals(RoundingPolicy.ROUND_HALF_UP, tmpl.rounding());
    assertEquals(shares, tmpl.shares());
    assertFalse(tmpl.deferred());
    assertEquals(1L, tmpl.lotSize());
  }

  @Test
  void ofWithLotSize() {
    AllocationTemplate tmpl =
        AllocationTemplate.of(
            "tmpl-1",
            1L,
            AllocationPolicy.PRO_RATA,
            RoundingPolicy.ROUND_HALF_UP,
            List.of(),
            10000L);
    assertEquals(10000L, tmpl.lotSize());
    assertFalse(tmpl.deferred());
  }

  @Test
  void deferredCreatesDeferredTemplate() {
    AllocationTemplate tmpl = AllocationTemplate.deferred("tmpl-1");
    assertEquals("tmpl-1", tmpl.templateId());
    assertTrue(tmpl.deferred());
    assertEquals(AllocationPolicy.PRO_RATA, tmpl.policy());
    assertEquals(RoundingPolicy.DISTRIBUTE_RESIDUAL, tmpl.rounding());
    assertTrue(tmpl.shares().isEmpty());
  }

  @Test
  void totalWeightBpsSumsShares() {
    List<AccountShare> shares =
        List.of(new AccountShare("a", "pb", 3000L), new AccountShare("b", "pb", 7000L));
    AllocationTemplate tmpl =
        AllocationTemplate.of(
            "tmpl-1", 1L, AllocationPolicy.PRO_RATA, RoundingPolicy.ROUND_HALF_UP, shares);
    assertEquals(10000L, tmpl.totalWeightBps());
  }

  @Test
  void nullSharesTreatedAsEmpty() {
    AllocationTemplate tmpl =
        AllocationTemplate.of(
            "tmpl-1", 1L, AllocationPolicy.PRO_RATA, RoundingPolicy.ROUND_HALF_UP, null);
    assertNotNull(tmpl.shares());
    assertTrue(tmpl.shares().isEmpty());
  }

  @Test
  void lotSizeLessThanOneThrows() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AllocationTemplate(
                "tmpl-1",
                1L,
                AllocationPolicy.PRO_RATA,
                RoundingPolicy.ROUND_HALF_UP,
                List.of(),
                false,
                0L));
  }

  @Test
  void nullPolicyThrows() {
    assertThrows(
        NullPointerException.class,
        () ->
            new AllocationTemplate(
                "tmpl-1", 1L, null, RoundingPolicy.ROUND_HALF_UP, List.of(), false, 1L));
  }
}
