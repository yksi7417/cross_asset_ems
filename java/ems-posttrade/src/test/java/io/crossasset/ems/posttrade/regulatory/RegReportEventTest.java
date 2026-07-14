/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.regulatory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RegReportEventTest {

  @Test
  void triggeredRecord() {
    RegReportEvent.RegReportTriggered e =
        new RegReportEvent.RegReportTriggered("r1", "event1", Regulator.TRACE);
    assertEquals("r1", e.reportId());
    assertEquals("event1", e.triggeredByEvent());
    assertEquals(Regulator.TRACE, e.regulator());
  }

  @Test
  void builtRecord() {
    RegReportEvent.RegReportBuilt e = new RegReportEvent.RegReportBuilt("r1", "abc123");
    assertEquals("r1", e.reportId());
    assertEquals("abc123", e.payloadHash());
  }

  @Test
  void deferredRecord() {
    RegReportEvent.RegReportDeferred e =
        new RegReportEvent.RegReportDeferred("r1", List.of("field1", "field2"));
    assertEquals("r1", e.reportId());
    assertEquals(2, e.missingFields().size());
    assertTrue(e.missingFields().contains("field1"));
  }

  @Test
  void submittedRecord() {
    RegReportEvent.RegReportSubmitted e = new RegReportEvent.RegReportSubmitted("r1", 1);
    assertEquals("r1", e.reportId());
    assertEquals(1, e.attempt());
  }

  @Test
  void ackedRecord() {
    RegReportEvent.RegReportAcked e = new RegReportEvent.RegReportAcked("r1", "ack-ref");
    assertEquals("r1", e.reportId());
    assertEquals("ack-ref", e.regulatorAckRef());
  }

  @Test
  void nackedRecord() {
    RegReportEvent.RegReportNacked e = new RegReportEvent.RegReportNacked("r1", "ERR", 2);
    assertEquals("r1", e.reportId());
    assertEquals("ERR", e.errorCode());
    assertEquals(2, e.nextAttempt());
  }

  @Test
  void failedRecord() {
    RegReportEvent.RegReportFailed e = new RegReportEvent.RegReportFailed("r1", 3);
    assertEquals("r1", e.reportId());
    assertEquals(3, e.afterAttempts());
  }

  @Test
  void voidedRecord() {
    RegReportEvent.RegReportVoided e =
        new RegReportEvent.RegReportVoided("r1", "r1:void", "reason");
    assertEquals("r1", e.reportId());
    assertEquals("r1:void", e.voidReportId());
    assertEquals("reason", e.reason());
  }

  @Test
  void amendedRecord() {
    RegReportEvent.RegReportAmended e =
        new RegReportEvent.RegReportAmended("r1", "r1:rep", "reason");
    assertEquals("r1", e.reportId());
    assertEquals("r1:rep", e.amendmentReportId());
    assertEquals("reason", e.reason());
  }
}
