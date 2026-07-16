/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class StageResultTest {

  @Test
  void testRejectedRecord() {
    StageResult.Rejected rej = new StageResult.Rejected("req-1", "EMS-ORD-3001", "test message");
    assertNotNull(rej);
    assertEquals("req-1", rej.requestId());
    assertEquals("EMS-ORD-3001", rej.rejectCode());
    assertEquals("test message", rej.message());
  }

  @Test
  void testClassExists() {
    assertNotNull(StageResult.class);
  }
}
