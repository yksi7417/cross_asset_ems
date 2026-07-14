/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RegulatorAdapterTest {

  @Test
  void interfaceReferenceNotNull() {
    assertNotNull(RegulatorAdapter.class);
  }

  @Test
  void submitResponseAcked() {
    RegulatorAdapter.SubmitResponse resp = RegulatorAdapter.SubmitResponse.acked("ack-ref");
    assertTrue(resp.acked());
    assertEquals("ack-ref", resp.ackRef());
    assertNull(resp.errorCode());
  }

  @Test
  void submitResponseNacked() {
    RegulatorAdapter.SubmitResponse resp = RegulatorAdapter.SubmitResponse.nacked("ERR");
    assertFalse(resp.acked());
    assertNull(resp.ackRef());
    assertEquals("ERR", resp.errorCode());
  }
}
