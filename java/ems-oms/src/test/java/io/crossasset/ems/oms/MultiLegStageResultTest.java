/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MultiLegStageResultTest {

  @Test
  void rejected_fields() {
    var result = new MultiLegStageResult.Rejected("requestId", "orderId", "code", "message");
    assertEquals("requestId", result.requestId());
    assertEquals("orderId", result.orderId());
    assertEquals("code", result.rejectCode());
    assertEquals("message", result.message());
  }
}
