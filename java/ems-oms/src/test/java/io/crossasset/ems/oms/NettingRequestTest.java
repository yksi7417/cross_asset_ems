/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class NettingRequestTest {

  @Test
  void testConstruction() {
    NettingRequest req = new NettingRequest("req-1", 123L, List.of(), "acct-1", true);
    assertNotNull(req);
    assertEquals("req-1", req.requestId());
    assertEquals(123L, req.sessionId());
    assertEquals("acct-1", req.account());
    assertEquals(true, req.netToZeroAllowed());
    assertNotNull(req.candidates());
    assertEquals(0, req.candidates().size());
  }

  @Test
  void testNullRequestIdThrows() {
    assertThrows(
        NullPointerException.class, () -> new NettingRequest(null, 1L, List.of(), "acct", true));
  }

  @Test
  void testNullAccountThrows() {
    assertThrows(
        NullPointerException.class, () -> new NettingRequest("req", 1L, List.of(), null, true));
  }

  @Test
  void testNullCandidatesThrows() {
    assertThrows(
        NullPointerException.class, () -> new NettingRequest("req", 1L, null, "acct", true));
  }
}
