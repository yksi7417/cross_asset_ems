/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class NetResultTest {

  @Test
  void rejected_fields() {
    var result = new NetResult.Rejected("requestId", "code", "message");
    assertEquals("requestId", result.requestId());
    assertEquals("code", result.rejectCode());
    assertEquals("message", result.message());
  }

  @Test
  void netted_empty() {
    var result = new NetResult.Netted(List.of(), List.of());
    assertNotNull(result.groups());
    assertNotNull(result.passthrough());
    assertTrue(result.groups().isEmpty());
    assertTrue(result.passthrough().isEmpty());
  }
}
