/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class RiskLimitsTest {

  @Test
  void setAndGet() {
    RiskLimits limits = new RiskLimits();
    RiskLimits.Limits value = new RiskLimits.Limits(100L, 200L, 300L, 400L);
    long v = limits.set(RiskLimits.Scope.FIRM, "FIRM1", value, "reason", "user");
    assertEquals(1L, v);
    assertEquals(1L, limits.version());
    Optional<RiskLimits.Limits> got = limits.get(RiskLimits.Scope.FIRM, "FIRM1");
    assertTrue(got.isPresent());
    assertEquals(100L, got.get().maxGrossNotionalHard());
    assertEquals(200L, got.get().maxGrossNotionalSoft());
    assertEquals(300L, got.get().maxNetNotionalHard());
    assertEquals(400L, got.get().maxNetNotionalSoft());
  }

  @Test
  void getMissingReturnsEmpty() {
    RiskLimits limits = new RiskLimits();
    Optional<RiskLimits.Limits> got = limits.get(RiskLimits.Scope.FIRM, "MISSING");
    assertTrue(got.isEmpty());
  }

  @Test
  void journalRecordsAmendment() {
    RiskLimits limits = new RiskLimits();
    RiskLimits.Limits value = new RiskLimits.Limits(100L, null, null, null);
    limits.set(RiskLimits.Scope.DESK, "DESK1", value, "reason", "user");
    limits.set(RiskLimits.Scope.ACCOUNT, "ACCT1", value, "reason2", "user2");
    assertEquals(2, limits.journal().size());
    RiskLimits.Amendment a = limits.journal().get(0);
    assertEquals(1L, a.version());
    assertEquals(RiskLimits.Scope.DESK, a.scope());
    assertEquals("DESK1", a.owner());
    assertEquals("reason", a.changeReason());
    assertEquals("user", a.signedOffBy());
  }

  @Test
  void versionIncrements() {
    RiskLimits limits = new RiskLimits();
    RiskLimits.Limits value = new RiskLimits.Limits(null, null, null, null);
    assertEquals(0L, limits.version());
    limits.set(RiskLimits.Scope.FIRM, "FIRM1", value, "r", "u");
    assertEquals(1L, limits.version());
    limits.set(RiskLimits.Scope.FIRM, "FIRM1", value, "r", "u");
    assertEquals(2L, limits.version());
  }

  @Test
  void scopeEnumValues() {
    assertNotNull(RiskLimits.Scope.FIRM);
    assertNotNull(RiskLimits.Scope.DESK);
    assertNotNull(RiskLimits.Scope.ACCOUNT);
    assertEquals(3, RiskLimits.Scope.values().length);
  }

  @Test
  void rejectsNullValue() {
    RiskLimits limits = new RiskLimits();
    assertThrows(
        NullPointerException.class,
        () -> limits.set(RiskLimits.Scope.FIRM, "FIRM1", null, "r", "u"));
  }

  @Test
  void rejectsNullChangeReason() {
    RiskLimits limits = new RiskLimits();
    RiskLimits.Limits value = new RiskLimits.Limits(null, null, null, null);
    assertThrows(
        NullPointerException.class,
        () -> limits.set(RiskLimits.Scope.FIRM, "FIRM1", value, null, "u"));
  }

  @Test
  void rejectsNullSignedOffBy() {
    RiskLimits limits = new RiskLimits();
    RiskLimits.Limits value = new RiskLimits.Limits(null, null, null, null);
    assertThrows(
        NullPointerException.class,
        () -> limits.set(RiskLimits.Scope.FIRM, "FIRM1", value, "r", null));
  }

  @Test
  void limitsRecordFields() {
    RiskLimits.Limits value = new RiskLimits.Limits(1L, 2L, 3L, 4L);
    assertEquals(1L, value.maxGrossNotionalHard());
    assertEquals(2L, value.maxGrossNotionalSoft());
    assertEquals(3L, value.maxNetNotionalHard());
    assertEquals(4L, value.maxNetNotionalSoft());
  }

  @Test
  void limitsRecordNulls() {
    RiskLimits.Limits value = new RiskLimits.Limits(null, null, null, null);
    assertEquals(Optional.of(value), Optional.ofNullable(value));
  }
}
