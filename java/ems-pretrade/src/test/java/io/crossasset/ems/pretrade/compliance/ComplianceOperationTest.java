/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ComplianceOperationTest {

  @Test
  void construction() {
    ComplianceOperation op =
        new ComplianceOperation(
            ComplianceOperation.Kind.STAGE,
            1L,
            "FIRM",
            "DESK",
            "USER",
            "ORD",
            "FIGI",
            1,
            100L,
            50L,
            "ACCT");
    assertEquals(ComplianceOperation.Kind.STAGE, op.kind());
    assertEquals(1L, op.sessionId());
    assertEquals("FIRM", op.firm());
    assertEquals("DESK", op.desk());
    assertEquals("USER", op.user());
    assertEquals("ORD", op.orderId());
    assertEquals("FIGI", op.figi());
    assertEquals(1, op.side());
    assertEquals(100L, op.qty());
    assertEquals(50L, op.price());
    assertEquals("ACCT", op.account());
  }

  @Test
  void nullableOrderId() {
    ComplianceOperation op =
        new ComplianceOperation(
            ComplianceOperation.Kind.ROUTE,
            2L,
            "FIRM",
            "DESK",
            "USER",
            null,
            "FIGI",
            1,
            100L,
            null,
            "ACCT");
    assertNull(op.orderId());
    assertNull(op.price());
  }

  @Test
  void rejectsNullKind() {
    assertThrows(
        NullPointerException.class,
        () ->
            new ComplianceOperation(
                null, 1L, "FIRM", "DESK", "USER", null, "FIGI", 1, 100L, null, "ACCT"));
  }

  @Test
  void rejectsNullFirm() {
    assertThrows(
        NullPointerException.class,
        () ->
            new ComplianceOperation(
                ComplianceOperation.Kind.STAGE,
                1L,
                null,
                "DESK",
                "USER",
                null,
                "FIGI",
                1,
                100L,
                null,
                "ACCT"));
  }

  @Test
  void kindEnumValues() {
    assertNotNull(ComplianceOperation.Kind.STAGE);
    assertNotNull(ComplianceOperation.Kind.AMEND);
    assertNotNull(ComplianceOperation.Kind.MARK_READY);
    assertNotNull(ComplianceOperation.Kind.ROUTE);
    assertEquals(4, ComplianceOperation.Kind.values().length);
  }
}
