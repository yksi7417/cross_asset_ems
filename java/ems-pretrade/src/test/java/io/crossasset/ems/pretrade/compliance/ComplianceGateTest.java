/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ComplianceGate}: ALLOW/BLOCK/WARN composition, full audit rule results,
 * pending-block store, raw resolution transitions. Per arch-compliance.md, task 10.1.
 */
class ComplianceGateTest {

  private static final OverridePath FOUR_EYES =
      new OverridePath(Set.of("#compliance-override-restricted-instrument"), 2, 3_600_000L, true);

  private static final ComplianceOperation OP =
      new ComplianceOperation(
          ComplianceOperation.Kind.STAGE,
          1L,
          "firm-a",
          "desk-1",
          "trader-1",
          null,
          "BBG000BLNNH6",
          1,
          100,
          null,
          "acc-1");

  @Test
  void allChecksAllow_decisionAllow_withFullRuleResults() {
    ComplianceGate gate = new ComplianceGate(List.of(allow("r1"), allow("r2")));
    ComplianceDecision decision = gate.evaluate(OP);
    assertThat(decision.outcome()).isEqualTo(ComplianceOutcome.ALLOW);
    assertThat(decision.blockId()).isNull();
    assertThat(decision.ruleResults()).hasSize(2);
    assertThat(decision.ruleResults()).allMatch(r -> r.outcome() == ComplianceOutcome.ALLOW);
  }

  @Test
  void blockingCheck_suspendsOperationIntoPendingStore() {
    ComplianceGate gate = new ComplianceGate(List.of(allow("r1"), block("r2", "too big")));
    ComplianceDecision decision = gate.evaluate(OP);
    assertThat(decision.outcome()).isEqualTo(ComplianceOutcome.BLOCK);
    assertThat(decision.blockId()).isNotNull();

    PendingBlock block = gate.findBlock(decision.blockId()).orElseThrow();
    assertThat(block.status()).isEqualTo(PendingBlock.Status.PENDING);
    assertThat(block.ruleId()).isEqualTo("r2");
    assertThat(block.rationale()).isEqualTo("too big");
    assertThat(block.overridePath()).isEqualTo(FOUR_EYES);
    assertThat(block.operation()).isEqualTo(OP);
    assertThat(gate.pendingBlocks()).hasSize(1);
  }

  @Test
  void warnOnly_decisionWarn_operationProceeds() {
    ComplianceGate gate = new ComplianceGate(List.of(warn("r1", "watch list")));
    ComplianceDecision decision = gate.evaluate(OP);
    assertThat(decision.outcome()).isEqualTo(ComplianceOutcome.WARN);
    assertThat(decision.blockId()).isNull();
    assertThat(gate.pendingBlocks()).isEmpty();
  }

  @Test
  void blockWinsOverWarn_andAllRulesStillEvaluated() {
    ComplianceGate gate =
        new ComplianceGate(List.of(warn("r1", "w"), block("r2", "b"), warn("r3", "w2")));
    ComplianceDecision decision = gate.evaluate(OP);
    assertThat(decision.outcome()).isEqualTo(ComplianceOutcome.BLOCK);
    assertThat(decision.ruleResults()).hasSize(3); // r3 evaluated despite the block
  }

  @Test
  void firstBlockingRule_ownsTheBlock() {
    ComplianceGate gate = new ComplianceGate(List.of(block("first", "a"), block("second", "b")));
    ComplianceDecision decision = gate.evaluate(OP);
    assertThat(gate.findBlock(decision.blockId()).orElseThrow().ruleId()).isEqualTo("first");
  }

  @Test
  void ids_areDeterministicSequences() {
    ComplianceGate gate = new ComplianceGate(List.of(block("r", "x")));
    ComplianceDecision first = gate.evaluate(OP);
    ComplianceDecision second = gate.evaluate(OP);
    assertThat(first.checkId()).isEqualTo("CMP-CHK-1");
    assertThat(second.checkId()).isEqualTo("CMP-CHK-2");
    assertThat(first.blockId()).isEqualTo("CMP-BLK-1");
    assertThat(second.blockId()).isEqualTo("CMP-BLK-2");
  }

  @Test
  void resolveBlock_released_leavesAuditTrail() {
    ComplianceGate gate = new ComplianceGate(List.of(block("r", "x")));
    String blockId = gate.evaluate(OP).blockId();
    PendingBlock released =
        gate.resolveBlock(blockId, PendingBlock.Status.RELEASED, "supervisor-1", "verified size")
            .orElseThrow();
    assertThat(released.status()).isEqualTo(PendingBlock.Status.RELEASED);
    assertThat(released.resolvedBy()).isEqualTo("supervisor-1");
    assertThat(released.resolutionRationale()).isEqualTo("verified size");
    assertThat(gate.pendingBlocks()).isEmpty();
  }

  @Test
  void resolveBlock_alreadyResolvedOrUnknown_returnsEmpty() {
    ComplianceGate gate = new ComplianceGate(List.of(block("r", "x")));
    String blockId = gate.evaluate(OP).blockId();
    gate.resolveBlock(blockId, PendingBlock.Status.DENIED, "sup", null);
    assertThat(gate.resolveBlock(blockId, PendingBlock.Status.RELEASED, "sup2", null)).isEmpty();
    assertThat(gate.resolveBlock("NOPE", PendingBlock.Status.RELEASED, "sup", null)).isEmpty();
    assertThat(gate.findBlock(blockId).orElseThrow().status())
        .isEqualTo(PendingBlock.Status.DENIED);
  }

  // ── check fixtures ──────────────────────────────────────────────────────────

  private static ComplianceCheck allow(String ruleId) {
    return check(ruleId, op -> Optional.empty());
  }

  private static ComplianceCheck block(String ruleId, String rationale) {
    return check(
        ruleId,
        op ->
            Optional.of(
                new ComplianceCheck.Finding(ComplianceOutcome.BLOCK, rationale, FOUR_EYES)));
  }

  private static ComplianceCheck warn(String ruleId, String rationale) {
    return check(
        ruleId,
        op -> Optional.of(new ComplianceCheck.Finding(ComplianceOutcome.WARN, rationale, null)));
  }

  private static ComplianceCheck check(
      String ruleId,
      java.util.function.Function<ComplianceOperation, Optional<ComplianceCheck.Finding>> fn) {
    return new ComplianceCheck() {
      @Override
      public String ruleId() {
        return ruleId;
      }

      @Override
      public Optional<Finding> evaluate(ComplianceOperation operation) {
        return fn.apply(operation);
      }
    };
  }
}
