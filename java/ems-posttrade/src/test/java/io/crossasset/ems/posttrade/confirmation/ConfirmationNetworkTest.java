/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.confirmation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConfirmationNetworkTest {

  @Test
  void affirmationResponseAccepted() {
    ConfirmationNetwork.AffirmationResponse resp =
        ConfirmationNetwork.AffirmationResponse.accepted();
    assertNotNull(resp);
    assertTrue(resp.affirmed());
    assertEquals(null, resp.reason());
  }

  @Test
  void affirmationResponseRejected() {
    ConfirmationNetwork.AffirmationResponse resp =
        ConfirmationNetwork.AffirmationResponse.rejected("bad qty");
    assertNotNull(resp);
    assertEquals(false, resp.affirmed());
    assertEquals("bad qty", resp.reason());
  }
}
