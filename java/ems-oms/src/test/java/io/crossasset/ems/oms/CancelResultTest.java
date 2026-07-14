/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class CancelResultTest {

  @Test
  void interfaceExists() {
    assertNotNull(CancelResult.class);
  }

  @Test
  void canceledRecordExists() {
    assertNotNull(CancelResult.Canceled.class);
  }

  @Test
  void rejectedRecordExists() {
    assertNotNull(CancelResult.Rejected.class);
  }
}
