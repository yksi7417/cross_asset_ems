/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.borrow;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.pretrade.borrow.BorrowService.BorrowPosition;
import io.crossasset.ems.pretrade.borrow.BorrowService.LocateRecord;
import io.crossasset.ems.pretrade.borrow.BorrowService.LocateResult;
import io.crossasset.ems.pretrade.compliance.ComplianceCheck;
import io.crossasset.ems.pretrade.compliance.ComplianceDecision;
import io.crossasset.ems.pretrade.compliance.ComplianceGate;
import io.crossasset.ems.pretrade.compliance.ComplianceOperation;
import io.crossasset.ems.pretrade.compliance.ComplianceOutcome;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Borrow/locate tests (task 18.6): internal-first/best-rate locate sourcing with reservation, TTL
 * expiry + cancel restoring availability, borrow lifecycle (execute / return / recall with
 * roll-or-forced-cover), daily cost accrual, the Reg SHO attestation, and the compliance-path
 * short-sale gate (locate-or-block, HTB tag gating, short-exempt WARN).
 */
class BorrowServiceTest {

  private static final String FIGI = "BBG000BLNNH6";
  private static final long TTL = 60_000L;

  private final BorrowService borrow = new BorrowService(TTL);

  // ── Locate sourcing ──────────────────────────────────────────────────────────

  @Test
  void locate_prefersInternalBook_thenBestExternalRate() {
    borrow.recordAvailability(FIGI, "pb-expensive", 10_000, 800);
    borrow.recordAvailability(FIGI, "pb-cheap", 10_000, 50);
    borrow.recordAvailability(FIGI, BorrowService.INTERNAL, 1_000, 25);

    LocateRecord first = located(borrow.locate(FIGI, 800, "t1", 1_000L));
    assertThat(first.lender()).isEqualTo(BorrowService.INTERNAL);
    assertThat(first.rateBp()).isEqualTo(25);

    // Internal now has 200 left — too small for 800; best external rate wins.
    LocateRecord second = located(borrow.locate(FIGI, 800, "t1", 1_000L));
    assertThat(second.lender()).isEqualTo("pb-cheap");
    assertThat(second.expiresAtMillis()).isEqualTo(1_000L + TTL);
  }

  @Test
  void locate_reservesAvailability_andFailsWhenInsufficient() {
    borrow.recordAvailability(FIGI, BorrowService.INTERNAL, 500, 25);

    assertThat(borrow.locate(FIGI, 400, "t1", 0L)).isInstanceOf(LocateResult.Located.class);
    LocateResult second = borrow.locate(FIGI, 400, "t1", 0L);
    assertThat(second).isInstanceOf(LocateResult.NotLocated.class);
    assertThat(((LocateResult.NotLocated) second).reason()).contains("insufficient availability");

    LocateResult unknown = borrow.locate("BBG0UNKNOWN0", 1, "t1", 0L);
    assertThat(((LocateResult.NotLocated) unknown).reason()).contains("no lender availability");
  }

  @Test
  void expiredAndCancelledLocates_restoreAvailability() {
    borrow.recordAvailability(FIGI, BorrowService.INTERNAL, 1_000, 25);
    LocateRecord locate = located(borrow.locate(FIGI, 600, "t1", 0L));

    assertThat(borrow.releaseExpired(TTL - 1)).isZero();
    assertThat(borrow.releaseExpired(TTL)).isEqualTo(1);
    assertThat(borrow.findLocate(locate.locateId()).orElseThrow().status())
        .isEqualTo(LocateRecord.Status.EXPIRED);
    assertThat(borrow.availability(FIGI).get(0).availableQty()).isEqualTo(1_000);

    LocateRecord again = located(borrow.locate(FIGI, 600, "t1", 0L));
    assertThat(borrow.cancelLocate(again.locateId())).isTrue();
    assertThat(borrow.cancelLocate(again.locateId())).as("double cancel").isFalse();
    assertThat(borrow.availability(FIGI).get(0).availableQty()).isEqualTo(1_000);
  }

  // ── Borrow lifecycle ─────────────────────────────────────────────────────────

  @Test
  void borrowLifecycle_executeReturn() {
    borrow.recordAvailability(FIGI, BorrowService.INTERNAL, 1_000, 100);
    LocateRecord locate = located(borrow.locate(FIGI, 700, "t1", 0L));

    BorrowPosition position = borrow.borrowExecuted(locate.locateId(), 5_000L).orElseThrow();
    assertThat(position.status()).isEqualTo(BorrowPosition.Status.OPEN);
    assertThat(position.qty()).isEqualTo(700);
    assertThat(borrow.findLocate(locate.locateId()).orElseThrow().status())
        .isEqualTo(LocateRecord.Status.CONSUMED);
    // A consumed locate cannot execute twice.
    assertThat(borrow.borrowExecuted(locate.locateId(), 5_000L)).isEmpty();

    assertThat(borrow.returnBorrow(position.positionId())).isTrue();
    assertThat(borrow.findPosition(position.positionId()).orElseThrow().status())
        .isEqualTo(BorrowPosition.Status.RETURNED);
  }

  @Test
  void recall_rollsToAnotherLender_orForcesCoverWithT3Deadline() {
    borrow.recordAvailability(FIGI, BorrowService.INTERNAL, 1_000, 25);
    LocateRecord locate = located(borrow.locate(FIGI, 1_000, "t1", 0L));
    BorrowPosition position = borrow.borrowExecuted(locate.locateId(), 0L).orElseThrow();

    // Another lender has supply: the borrow rolls, stays OPEN, new lender + rate.
    borrow.recordAvailability(FIGI, "pb-roll", 5_000, 75);
    BorrowPosition rolled = borrow.recall(position.positionId(), 10_000L).orElseThrow();
    assertThat(rolled.status()).isEqualTo(BorrowPosition.Status.OPEN);
    assertThat(rolled.lender()).isEqualTo("pb-roll");
    assertThat(borrow.recallQueue()).isEmpty();

    // No supply anywhere: forced cover, T+3 deadline, ops queue.
    borrow.recordAvailability(FIGI, "pb-roll", 0, 75);
    BorrowPosition recalled = borrow.recall(position.positionId(), 20_000L).orElseThrow();
    assertThat(recalled.status()).isEqualTo(BorrowPosition.Status.RECALLED);
    assertThat(recalled.coverDeadlineMillis()).isEqualTo(20_000L + 3L * 24 * 60 * 60 * 1000);
    assertThat(borrow.recallQueue()).hasSize(1);
  }

  @Test
  void costAccrual_perDay() {
    borrow.recordAvailability(FIGI, BorrowService.INTERNAL, 1_000, 365); // 3.65% annual
    LocateRecord locate = located(borrow.locate(FIGI, 1_000, "t1", 0L));
    BorrowPosition position = borrow.borrowExecuted(locate.locateId(), 0L).orElseThrow();

    // $100,000 notional (1_000 sh * $100.0000) at 3.65% annual = $10/day = 100_000 fp.
    long day = borrow.accrueDaily(position.positionId(), 1_000_000L);
    assertThat(day).isEqualTo(100_000L);
    borrow.accrueDaily(position.positionId(), 1_000_000L);
    assertThat(borrow.findPosition(position.positionId()).orElseThrow().accruedCost())
        .isEqualTo(200_000L);
  }

  // ── Reg SHO attestation ──────────────────────────────────────────────────────

  @Test
  void regShoAttestation_summarizesLocatesBorrowsAndThresholdExposure() {
    borrow.recordAvailability(FIGI, BorrowService.INTERNAL, 10_000, 25);
    borrow.setThresholdSecurity(FIGI, true);
    LocateRecord consumed = located(borrow.locate(FIGI, 100, "t1", 0L));
    borrow.borrowExecuted(consumed.locateId(), 0L);
    located(borrow.locate(FIGI, 100, "t1", 0L)); // stays ACTIVE
    located(borrow.locate(FIGI, 100, "t1", 0L));
    borrow.releaseExpired(TTL); // expires the two ACTIVE ones

    var attestation = borrow.regShoAttestation(99_000L);
    assertThat(attestation.locatesConsumed()).isEqualTo(1);
    assertThat(attestation.locatesExpired()).isEqualTo(2);
    assertThat(attestation.openBorrows()).isEqualTo(1);
    assertThat(attestation.thresholdSecuritiesWithOpenBorrows()).containsExactly(FIGI);
  }

  // ── Compliance-path gate ─────────────────────────────────────────────────────

  private ComplianceOperation shortSale(int side, long qty) {
    return new ComplianceOperation(
        ComplianceOperation.Kind.STAGE,
        1L,
        "firm",
        "desk",
        "trader-1",
        null,
        FIGI,
        side,
        qty,
        null,
        "acc");
  }

  @Test
  void complianceGate_locatedShortPasses_nakedShortBlocksWithOverridePath() {
    borrow.recordAvailability(FIGI, BorrowService.INTERNAL, 500, 25);
    ComplianceCheck check = new ShortSaleLocateCheck(borrow, user -> Set.of(), () -> 0L);
    ComplianceGate gate = new ComplianceGate(List.of(check));

    ComplianceDecision located = gate.evaluate(shortSale(5, 400));
    assertThat(located.outcome()).isEqualTo(ComplianceOutcome.ALLOW);
    // The documented determination exists in the borrow journal.
    assertThat(borrow.findLocate("LOC-1").orElseThrow().requestedBy()).isEqualTo("trader-1");

    ComplianceDecision naked = gate.evaluate(shortSale(5, 400));
    assertThat(naked.outcome()).isEqualTo(ComplianceOutcome.BLOCK);
    var pending = gate.findBlock(naked.blockId()).orElseThrow();
    assertThat(pending.overridePath().requiredTags())
        .containsExactly(ShortSaleLocateCheck.NAKED_SHORT_OVERRIDE_TAG);
    assertThat(pending.rationale()).contains("naked short blocked");
  }

  @Test
  void complianceGate_htbRequiresTag_buysAndLongSellsUntouched() {
    borrow.recordAvailability(FIGI, BorrowService.INTERNAL, 10_000, 2_500);
    borrow.setHardToBorrow(FIGI, true);
    Map<String, Set<String>> tags = Map.of("senior", Set.of(ShortSaleLocateCheck.HTB_TAG));
    ComplianceCheck check =
        new ShortSaleLocateCheck(borrow, user -> tags.getOrDefault(user, Set.of()), () -> 0L);
    ComplianceGate gate = new ComplianceGate(List.of(check));

    ComplianceDecision blocked = gate.evaluate(shortSale(5, 100));
    assertThat(blocked.outcome()).isEqualTo(ComplianceOutcome.BLOCK);
    assertThat(gate.findBlock(blocked.blockId()).orElseThrow().rationale())
        .contains("Hard-to-borrow");

    ComplianceOperation seniorShort =
        new ComplianceOperation(
            ComplianceOperation.Kind.STAGE,
            1L,
            "firm",
            "desk",
            "senior",
            null,
            FIGI,
            5,
            100,
            null,
            "acc");
    assertThat(gate.evaluate(seniorShort).outcome()).isEqualTo(ComplianceOutcome.ALLOW);

    // Buys and long sells never consult the borrow service.
    assertThat(gate.evaluate(shortSale(1, 100)).outcome()).isEqualTo(ComplianceOutcome.ALLOW);
    assertThat(gate.evaluate(shortSale(2, 100)).outcome()).isEqualTo(ComplianceOutcome.ALLOW);
  }

  @Test
  void complianceGate_shortExempt_warnsButPasses() {
    ComplianceCheck check = new ShortSaleLocateCheck(borrow, user -> Set.of(), () -> 0L);
    ComplianceGate gate = new ComplianceGate(List.of(check));

    ComplianceDecision exempt = gate.evaluate(shortSale(6, 100));
    assertThat(exempt.outcome()).isEqualTo(ComplianceOutcome.WARN);
  }

  private static LocateRecord located(LocateResult result) {
    assertThat(result).isInstanceOf(LocateResult.Located.class);
    return ((LocateResult.Located) result).locate();
  }
}
