/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ComplianceListServiceTest {

  private ComplianceListService service;

  @BeforeEach
  void setUp() {
    service = new ComplianceListService();
  }

  @Test
  void initialVersionIsZero() {
    assertEquals(0, service.version());
  }

  @Test
  void addReturnsMonotonicallyIncreasingVersion() {
    long v1 = service.add(ComplianceListService.Kind.RESTRICTED, "firm-A", "FIGI-1", 100, null);
    long v2 = service.add(ComplianceListService.Kind.RESTRICTED, "firm-A", "FIGI-2", 100, null);
    assertTrue(v2 > v1);
  }

  @Test
  void addMakesEntryActive() {
    service.add(ComplianceListService.Kind.RESTRICTED, "firm-A", "FIGI-1", 100, null);
    assertTrue(service.isActive(ComplianceListService.Kind.RESTRICTED, "firm-A", "FIGI-1", 200));
  }

  @Test
  void addWithExpiry() {
    service.add(ComplianceListService.Kind.WATCH, "firm-A", "FIGI-1", 100, 200L);
    assertTrue(service.isActive(ComplianceListService.Kind.WATCH, "firm-A", "FIGI-1", 150));
    assertFalse(service.isActive(ComplianceListService.Kind.WATCH, "firm-A", "FIGI-1", 200));
  }

  @Test
  void addBeforeEffectiveFromNotActive() {
    service.add(ComplianceListService.Kind.RESTRICTED, "firm-A", "FIGI-1", 1000, null);
    assertFalse(service.isActive(ComplianceListService.Kind.RESTRICTED, "firm-A", "FIGI-1", 500));
  }

  @Test
  void removeReturnsEntry() {
    service.add(ComplianceListService.Kind.RESTRICTED, "firm-A", "FIGI-1", 100, null);
    long vBefore = service.version();
    long vAfter = service.remove(ComplianceListService.Kind.RESTRICTED, "firm-A", "FIGI-1");
    assertTrue(vAfter > vBefore);
    assertFalse(service.isActive(ComplianceListService.Kind.RESTRICTED, "firm-A", "FIGI-1", 200));
  }

  @Test
  void removeAbsentReturnsSameVersion() {
    long vBefore = service.version();
    long vAfter = service.remove(ComplianceListService.Kind.RESTRICTED, "firm-A", "NO-SUCH");
    assertEquals(vBefore, vAfter);
  }

  @Test
  void removeNonExistentOwner() {
    long vBefore = service.version();
    long vAfter = service.remove(ComplianceListService.Kind.RESTRICTED, "NO-OWNER", "FIGI-1");
    assertEquals(vBefore, vAfter);
  }

  @Test
  void setAllowListModeTurnsOn() {
    assertFalse(service.usesAllowList("desk-1"));
    long v = service.setAllowListMode("desk-1", true);
    assertTrue(v > 0);
    assertTrue(service.usesAllowList("desk-1"));
  }

  @Test
  void setAllowListModeTurnsOff() {
    service.setAllowListMode("desk-1", true);
    long v = service.setAllowListMode("desk-1", false);
    assertTrue(v > 0);
    assertFalse(service.usesAllowList("desk-1"));
  }

  @Test
  void setAllowListModeNoOpWhenAlreadySet() {
    service.setAllowListMode("desk-1", true);
    long vBefore = service.version();
    long vAfter = service.setAllowListMode("desk-1", true);
    assertEquals(vBefore, vAfter);
  }

  @Test
  void isActiveReturnsFalseForUnknownList() {
    assertFalse(service.isActive(ComplianceListService.Kind.RESTRICTED, "firm-A", "FIGI-1", 200));
  }

  @Test
  void isActiveReturnsFalseForUnknownFigi() {
    service.add(ComplianceListService.Kind.RESTRICTED, "firm-A", "FIGI-1", 100, null);
    assertFalse(service.isActive(ComplianceListService.Kind.RESTRICTED, "firm-A", "FIGI-999", 200));
  }

  @Test
  void journalRecordsAdd() {
    service.add(ComplianceListService.Kind.RESTRICTED, "firm-A", "FIGI-1", 100, null);
    List<ComplianceListService.ListChange> journal = service.journal();
    assertEquals(1, journal.size());
    var change = journal.get(0);
    assertEquals("ADD", change.action());
    assertEquals(ComplianceListService.Kind.RESTRICTED, change.kind());
    assertEquals("firm-A", change.owner());
    assertEquals("FIGI-1", change.figi());
  }

  @Test
  void journalRecordsRemove() {
    service.add(ComplianceListService.Kind.RESTRICTED, "firm-A", "FIGI-1", 100, null);
    service.remove(ComplianceListService.Kind.RESTRICTED, "firm-A", "FIGI-1");
    List<ComplianceListService.ListChange> journal = service.journal();
    assertEquals(2, journal.size());
    assertEquals("REMOVE", journal.get(1).action());
  }

  @Test
  void journalRecordsAllowModeChange() {
    service.setAllowListMode("desk-1", true);
    List<ComplianceListService.ListChange> journal = service.journal();
    assertEquals(1, journal.size());
    assertEquals("ALLOW_MODE_ON", journal.get(0).action());
  }

  @Test
  void journalIsUnmodifiableSnapshot() {
    service.add(ComplianceListService.Kind.RESTRICTED, "firm-A", "FIGI-1", 100, null);
    List<ComplianceListService.ListChange> journal = service.journal();
    assertThrows(
        UnsupportedOperationException.class,
        () -> journal.add(new ComplianceListService.ListChange(0, "X", null, null, null)));
  }

  @Test
  void versionMonotonicAcrossOperations() {
    service.add(ComplianceListService.Kind.RESTRICTED, "firm-A", "FIGI-1", 100, null);
    service.add(ComplianceListService.Kind.WATCH, "firm-A", "FIGI-2", 100, null);
    service.setAllowListMode("desk-1", true);
    assertTrue(service.version() >= 3);
  }

  @Test
  void entryActiveAtBoundary() {
    var entry = new ComplianceListService.Entry("FIGI-1", 100, 200L);
    assertTrue(entry.activeAt(100));
    assertTrue(entry.activeAt(199));
    assertFalse(entry.activeAt(200));
  }

  @Test
  void entryActiveAtWithNullExpiry() {
    var entry = new ComplianceListService.Entry("FIGI-1", 100, null);
    assertTrue(entry.activeAt(Long.MAX_VALUE));
  }

  @Test
  void entryActiveAtBeforeEffective() {
    var entry = new ComplianceListService.Entry("FIGI-1", 100, 200L);
    assertFalse(entry.activeAt(99));
  }

  @Test
  void listChangeRecordFields() {
    var change =
        new ComplianceListService.ListChange(
            1, "ADD", ComplianceListService.Kind.RESTRICTED, "firm-A", "FIGI-1");
    assertEquals(1, change.version());
    assertEquals("ADD", change.action());
    assertEquals(ComplianceListService.Kind.RESTRICTED, change.kind());
    assertEquals("firm-A", change.owner());
    assertEquals("FIGI-1", change.figi());
  }

  @Test
  void differentKindsAreIndependent() {
    service.add(ComplianceListService.Kind.RESTRICTED, "firm-A", "FIGI-1", 100, null);
    service.add(ComplianceListService.Kind.WATCH, "firm-A", "FIGI-1", 100, null);
    assertTrue(service.isActive(ComplianceListService.Kind.RESTRICTED, "firm-A", "FIGI-1", 200));
    assertTrue(service.isActive(ComplianceListService.Kind.WATCH, "firm-A", "FIGI-1", 200));
  }

  @Test
  void differentOwnersAreIndependent() {
    service.add(ComplianceListService.Kind.RESTRICTED, "firm-A", "FIGI-1", 100, null);
    service.add(ComplianceListService.Kind.RESTRICTED, "firm-B", "FIGI-1", 100, null);
    assertTrue(service.isActive(ComplianceListService.Kind.RESTRICTED, "firm-A", "FIGI-1", 200));
    assertTrue(service.isActive(ComplianceListService.Kind.RESTRICTED, "firm-B", "FIGI-1", 200));
  }

  @Test
  void replaceEntryUpdatesVersion() {
    service.add(ComplianceListService.Kind.RESTRICTED, "firm-A", "FIGI-1", 100, null);
    long v1 = service.version();
    service.add(ComplianceListService.Kind.RESTRICTED, "firm-A", "FIGI-1", 200, null);
    long v2 = service.version();
    assertTrue(v2 > v1);
  }

  @Test
  void kindEnumValues() {
    assertNotNull(ComplianceListService.Kind.RESTRICTED);
    assertNotNull(ComplianceListService.Kind.ALLOW);
    assertNotNull(ComplianceListService.Kind.WATCH);
    assertEquals(3, ComplianceListService.Kind.values().length);
  }
}
