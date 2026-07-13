/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;

class PendingBlockTest {

  private ComplianceOperation sampleOp() {
    return new ComplianceOperation(
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
  }

  private OverridePath samplePath() {
    return new OverridePath(Set.of("TAG"), 1, 1000L, true);
  }

  @Test
  void construction() {
    PendingBlock block =
        new PendingBlock(
            "BLOCK1",
            "CHECK1",
            sampleOp(),
            "RULE1",
            "rationale",
            samplePath(),
            PendingBlock.Status.PENDING,
            null,
            null);
    assertEquals("BLOCK1", block.blockId());
    assertEquals("CHECK1", block.checkId());
    assertEquals(sampleOp(), block.operation());
    assertEquals("RULE1", block.ruleId());
    assertEquals("rationale", block.rationale());
    assertEquals(samplePath(), block.overridePath());
    assertEquals(PendingBlock.Status.PENDING, block.status());
    assertNull(block.resolvedBy());
    assertNull(block.resolutionRationale());
  }

  @Test
  void releasedStatus() {
    PendingBlock block =
        new PendingBlock(
            "BLOCK1",
            "CHECK1",
            sampleOp(),
            "RULE1",
            "rationale",
            samplePath(),
            PendingBlock.Status.RELEASED,
            "USER",
            "ok");
    assertEquals(PendingBlock.Status.RELEASED, block.status());
    assertEquals("USER", block.resolvedBy());
    assertEquals("ok", block.resolutionRationale());
  }

  @Test
  void rejectsNullBlockId() {
    assertThrows(
        NullPointerException.class,
        () ->
            new PendingBlock(
                null,
                "CHECK1",
                sampleOp(),
                "RULE1",
                "rationale",
                samplePath(),
                PendingBlock.Status.PENDING,
                null,
                null));
  }

  @Test
  void rejectsNullCheckId() {
    assertThrows(
        NullPointerException.class,
        () ->
            new PendingBlock(
                "BLOCK1",
                null,
                sampleOp(),
                "RULE1",
                "rationale",
                samplePath(),
                PendingBlock.Status.PENDING,
                null,
                null));
  }

  @Test
  void rejectsNullOperation() {
    assertThrows(
        NullPointerException.class,
        () ->
            new PendingBlock(
                "BLOCK1",
                "CHECK1",
                null,
                "RULE1",
                "rationale",
                samplePath(),
                PendingBlock.Status.PENDING,
                null,
                null));
  }

  @Test
  void rejectsNullRuleId() {
    assertThrows(
        NullPointerException.class,
        () ->
            new PendingBlock(
                "BLOCK1",
                "CHECK1",
                sampleOp(),
                null,
                "rationale",
                samplePath(),
                PendingBlock.Status.PENDING,
                null,
                null));
  }

  @Test
  void rejectsNullRationale() {
    assertThrows(
        NullPointerException.class,
        () ->
            new PendingBlock(
                "BLOCK1",
                "CHECK1",
                sampleOp(),
                "RULE1",
                null,
                samplePath(),
                PendingBlock.Status.PENDING,
                null,
                null));
  }

  @Test
  void rejectsNullOverridePath() {
    assertThrows(
        NullPointerException.class,
        () ->
            new PendingBlock(
                "BLOCK1",
                "CHECK1",
                sampleOp(),
                "RULE1",
                "rationale",
                null,
                PendingBlock.Status.PENDING,
                null,
                null));
  }

  @Test
  void rejectsNullStatus() {
    assertThrows(
        NullPointerException.class,
        () ->
            new PendingBlock(
                "BLOCK1",
                "CHECK1",
                sampleOp(),
                "RULE1",
                "rationale",
                samplePath(),
                null,
                null,
                null));
  }

  @Test
  void statusEnumValues() {
    assertNotNull(PendingBlock.Status.PENDING);
    assertNotNull(PendingBlock.Status.RELEASED);
    assertNotNull(PendingBlock.Status.DENIED);
    assertEquals(3, PendingBlock.Status.values().length);
  }
}
