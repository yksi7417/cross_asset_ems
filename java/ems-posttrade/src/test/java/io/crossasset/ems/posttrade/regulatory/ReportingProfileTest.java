/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ReportingProfileTest {

  @Test
  void mockProfile() {
    ReportingProfile profile = ReportingProfile.mock(Regulator.TRACE);
    assertNotNull(profile);
    assertEquals(Regulator.TRACE, profile.regulator());
    assertEquals("MOCK", profile.wireFormat());
    assertEquals(15 * 60 * 1000L, profile.maxLatencyMillis());
    assertEquals(3, profile.maxRetries());
    assertEquals(ReportingProfile.AmendmentProtocol.VOID_AND_REPLACE, profile.amendmentProtocol());
    assertNotNull(profile.requiredFields());
    assertEquals(3, profile.requiredFields().size());
  }

  @Test
  void traceProfile() {
    ReportingProfile profile = ReportingProfile.trace();
    assertNotNull(profile);
    assertEquals(Regulator.TRACE, profile.regulator());
    assertEquals("FIX_TRACE", profile.wireFormat());
    assertEquals(15 * 60 * 1000L, profile.maxLatencyMillis());
    assertEquals(3, profile.maxRetries());
    assertEquals(ReportingProfile.AmendmentProtocol.VOID_AND_REPLACE, profile.amendmentProtocol());
    assertNotNull(profile.requiredFields());
    assertEquals(10, profile.requiredFields().size());
  }
}
