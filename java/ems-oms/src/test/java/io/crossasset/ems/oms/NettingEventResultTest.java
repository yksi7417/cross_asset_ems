/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NettingEventResultTest {

  @Test
  void rejected_fields() {
    var result = new NettingEventResult.Rejected("groupId", "code", "message");
    assertEquals("groupId", result.groupId());
    assertEquals("code", result.rejectCode());
    assertEquals("message", result.message());
  }
}
