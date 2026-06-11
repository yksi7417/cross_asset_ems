/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.risk;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.pretrade.compliance.ComplianceOutcome;
import io.crossasset.ems.pretrade.position.PositionService;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RiskEngine} + {@link RiskLimits}: hard caps block with the risk-officer
 * override, soft caps warn, net vs gross distinction, scope cascade, unmarked operations warn,
 * parameter versioning. Per arch-risk-engine.md, task 10.6.
 */
class RiskEngineTest {

  private static final String FIGI = "BBG000BLNNH6";
  private static final long MARK = 100L;

  private final PositionService positions = new PositionService();
  private final RiskLimits limits = new RiskLimits();
  private final RiskEngine engine = new RiskEngine(positions, limits);

  @Test
  void underAllCaps_allow() {
    accountLimits(null, null, 100_000L, null);
    RiskEngine.RiskDecision decision = engine.preTradeCheck(buy(100, MARK));
    assertThat(decision.decision()).isEqualTo(ComplianceOutcome.ALLOW);
    assertThat(decision.results()).hasSize(1); // the evaluated net-hard row, ALLOW
  }

  @Test
  void hardCapBreach_blocksWithRiskOverride() {
    accountLimits(null, null, 5_000L, null);
    RiskEngine.RiskDecision decision = engine.preTradeCheck(buy(100, MARK)); // 10_000 net
    assertThat(decision.decision()).isEqualTo(ComplianceOutcome.BLOCK);
    assertThat(decision.overridePath().requiredTags()).contains("#risk-override-notional");
    assertThat(decision.results())
        .anyMatch(
            r -> r.check().equals("net_notional_hard") && r.outcome() == ComplianceOutcome.BLOCK);
  }

  @Test
  void softCapBreach_warns() {
    accountLimits(null, 5_000L, 100_000L, null);
    RiskEngine.RiskDecision decision = engine.preTradeCheck(buy(100, MARK)); // gross 10_000
    assertThat(decision.decision()).isEqualTo(ComplianceOutcome.WARN);
    assertThat(decision.overridePath()).isNull();
  }

  @Test
  void offsettingSell_reducesNetButCountsGross() {
    positions.applyFill(new PositionService.Fill("X-1", "acc-1", FIGI, 1, 100, MARK, 1));
    // Selling 60 against long 100: post-net 40*100=4k under net cap; post-gross 160*100=16k.
    accountLimits(20_000L, null, 5_000L, null);
    RiskEngine.RiskDecision decision = engine.preTradeCheck(sell(60, MARK));
    assertThat(decision.decision()).isEqualTo(ComplianceOutcome.ALLOW);

    accountLimits(15_000L, null, 5_000L, null); // tighten gross under 16k
    RiskEngine.RiskDecision blocked = engine.preTradeCheck(sell(60, MARK));
    assertThat(blocked.decision()).isEqualTo(ComplianceOutcome.BLOCK);
    assertThat(blocked.results())
        .anyMatch(
            r -> r.check().equals("gross_notional_hard") && r.outcome() == ComplianceOutcome.BLOCK);
  }

  @Test
  void deskScopeCap_appliesIndependently() {
    limits.set(
        RiskLimits.Scope.DESK,
        "desk-1",
        new RiskLimits.Limits(null, null, 5_000L, null),
        "desk cap",
        "risk-officer-1");
    RiskEngine.RiskDecision decision = engine.preTradeCheck(buy(100, MARK));
    assertThat(decision.decision()).isEqualTo(ComplianceOutcome.BLOCK);
    assertThat(decision.results()).anyMatch(r -> r.scope() == RiskLimits.Scope.DESK);
  }

  @Test
  void noMark_warnsRatherThanSilentlyAllowing() {
    accountLimits(null, null, 1L, null); // tightest possible cap — must not be bypassed silently
    RiskEngine.RiskDecision decision =
        engine.preTradeCheck(
            new RiskEngine.RiskOperation("firm-a", "desk-1", "acc-1", FIGI, 1, 100, null));
    assertThat(decision.decision()).isEqualTo(ComplianceOutcome.WARN);
    assertThat(decision.results().get(0).check()).contains("no_mark");
  }

  @Test
  void parameterVersion_recordedOnDecision_andJournaled() {
    long v1 = accountLimits(null, null, 5_000L, null);
    RiskEngine.RiskDecision first = engine.preTradeCheck(buy(100, MARK));
    assertThat(first.parameterVersion()).isEqualTo(v1);
    long v2 = accountLimits(null, null, 50_000L, null);
    RiskEngine.RiskDecision second = engine.preTradeCheck(buy(100, MARK));
    assertThat(second.parameterVersion()).isEqualTo(v2);
    assertThat(second.decision()).isEqualTo(ComplianceOutcome.ALLOW);
    assertThat(limits.journal()).hasSize(2);
    assertThat(limits.journal().get(0).signedOffBy()).isEqualTo("risk-officer-1");
  }

  @Test
  void noLimitsRegistered_allows() {
    RiskEngine.RiskDecision decision = engine.preTradeCheck(buy(1_000_000, MARK));
    assertThat(decision.decision()).isEqualTo(ComplianceOutcome.ALLOW);
    assertThat(decision.results()).isEmpty();
  }

  // ── helpers ─────────────────────────────────────────────────────────────────

  private long accountLimits(
      @Nullable Long grossHard,
      @Nullable Long grossSoft,
      @Nullable Long netHard,
      @Nullable Long netSoft) {
    return limits.set(
        RiskLimits.Scope.ACCOUNT,
        "acc-1",
        new RiskLimits.Limits(grossHard, grossSoft, netHard, netSoft),
        "test",
        "risk-officer-1");
  }

  private static RiskEngine.RiskOperation buy(long qty, long mark) {
    return new RiskEngine.RiskOperation("firm-a", "desk-1", "acc-1", FIGI, 1, qty, mark);
  }

  private static RiskEngine.RiskOperation sell(long qty, long mark) {
    return new RiskEngine.RiskOperation("firm-a", "desk-1", "acc-1", FIGI, 2, qty, mark);
  }
}
