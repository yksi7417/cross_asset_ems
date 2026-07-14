/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class InMemoryRegulatoryReportingServiceTest {

  @Test
  void constructorAcceptsDeterminer() {
    RegulatorDeterminer determiner = RegulatorDeterminer.usDefaults();
    InMemoryRegulatoryReportingService service = new InMemoryRegulatoryReportingService(determiner);
    assertNotNull(service);
  }

  @Test
  void registerAdapterAcceptsMock() {
    RegulatorDeterminer determiner = RegulatorDeterminer.usDefaults();
    InMemoryRegulatoryReportingService service = new InMemoryRegulatoryReportingService(determiner);
    RegulatorAdapter adapter = MockRegulatorAdapter.acking(Regulator.TRACE);
    service.registerAdapter(adapter);
  }
}
