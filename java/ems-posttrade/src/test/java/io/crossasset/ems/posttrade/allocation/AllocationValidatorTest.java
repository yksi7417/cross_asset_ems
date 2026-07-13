/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class AllocationValidatorTest {

  @Test
  void permissiveReturnsNull() {
    AllocationValidator v = AllocationValidator.permissive();
    assertNotNull(v);
    AllocationTemplate tmpl =
        AllocationTemplate.of(
            "tmpl-1", 1L, AllocationPolicy.PRO_RATA, RoundingPolicy.ROUND_HALF_UP, List.of());
    AccountShare share = new AccountShare("acct-1", "pb-1", 5000L);
    assertNull(v.rejectionReason(share, tmpl));
  }

  @Test
  void customRejects() {
    AllocationValidator v = (share, template) -> "not allowed";
    AllocationTemplate tmpl =
        AllocationTemplate.of(
            "tmpl-1", 1L, AllocationPolicy.PRO_RATA, RoundingPolicy.ROUND_HALF_UP, List.of());
    AccountShare share = new AccountShare("acct-1", "pb-1", 5000L);
    assertEquals("not allowed", v.rejectionReason(share, tmpl));
  }
}
