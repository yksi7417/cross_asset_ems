/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.confirmation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ConfirmationStateTest {

  @Test
  void allStatesNotNull() {
    for (ConfirmationState state : ConfirmationState.values()) {
      assertNotNull(state);
    }
  }

  @Test
  void pendingExists() {
    assertNotNull(ConfirmationState.PENDING);
  }

  @Test
  void matchedExists() {
    assertNotNull(ConfirmationState.MATCHED);
  }

  @Test
  void voidedExists() {
    assertNotNull(ConfirmationState.VOIDED);
  }
}
