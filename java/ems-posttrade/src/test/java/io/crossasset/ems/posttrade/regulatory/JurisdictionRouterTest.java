/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class JurisdictionRouterTest {

  @Test
  void homeReturnsThis() {
    JurisdictionRouter router = new JurisdictionRouter();
    JurisdictionRouter result = router.home("firm1", RegulatorDeterminer.usDefaults());
    assertSame(router, result);
  }

  @Test
  void homeCanBeChained() {
    JurisdictionRouter router = new JurisdictionRouter();
    router.home("firm1", RegulatorDeterminer.usDefaults());
    router.home("firm2", RegulatorDeterminer.crossAssetUs());
  }
}
