/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class NettingCandidateTest {

  @Test
  void candidate_fields() {
    var candidate =
        new NettingCandidate("orderId", "ccyPair", "valueDate", "accountGroup", null, false);
    assertEquals("orderId", candidate.orderId());
    assertEquals("ccyPair", candidate.ccyPair());
    assertEquals("valueDate", candidate.valueDate());
    assertEquals("accountGroup", candidate.accountGroup());
    assertNull(candidate.pac());
    assertFalse(candidate.doNotNet());
  }
}
