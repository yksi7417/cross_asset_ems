/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class MarkReadyResultTest {

  @Test
  void interfaceExists() {
    assertNotNull(MarkReadyResult.class);
  }

  @Test
  void readyRecordExists() {
    assertNotNull(MarkReadyResult.Ready.class);
  }

  @Test
  void rejectedRecordExists() {
    assertNotNull(MarkReadyResult.Rejected.class);
  }
}
