/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.confirmation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class MatchResultTest {

  @Test
  void ofMatchReturnsMatched() {
    MatchResult result = MatchResult.ofMatch();
    assertTrue(result.matched());
    assertTrue(result.differingFields().isEmpty());
  }

  @Test
  void ofMismatchReturnsUnmatched() {
    MatchResult result = MatchResult.ofMismatch(List.of("price", "qty"));
    assertFalse(result.matched());
    assertEquals(2, result.differingFields().size());
  }
}
