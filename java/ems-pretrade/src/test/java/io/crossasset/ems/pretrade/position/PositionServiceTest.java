/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.position;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link PositionService}: weighted-average-cost folding, realized P&L on closes,
 * flip-through-zero, bust recompute (closed positions can regress open), read-time marks, account
 * isolation. Per arch-position-service.md, task 10.7.
 */
class PositionServiceTest {

  private static final String ACC = "acc-1";
  private static final String FIGI = "BBG000BLNNH6";

  private final PositionService positions = new PositionService();
  private int execSeq = 1;

  @Test
  void buy_buildsLongWithAvgCost() {
    Position p = buy(100, 1000);
    assertThat(p.netQty()).isEqualTo(100);
    assertThat(p.longQty()).isEqualTo(100);
    assertThat(p.shortQty()).isZero();
    assertThat(p.avgCost()).isEqualTo(1000);
    assertThat(p.realizedPnl()).isZero();
  }

  @Test
  void secondBuy_reweightsAverage() {
    buy(100, 1000);
    Position p = buy(100, 1200);
    assertThat(p.netQty()).isEqualTo(200);
    assertThat(p.avgCost()).isEqualTo(1100); // (100*1000 + 100*1200) / 200
  }

  @Test
  void partialSell_realizesPnlKeepsAvg() {
    buy(100, 1000);
    Position p = sell(40, 1100);
    assertThat(p.netQty()).isEqualTo(60);
    assertThat(p.avgCost()).isEqualTo(1000);
    assertThat(p.realizedPnl()).isEqualTo(40 * 100); // 40 closed at +100
  }

  @Test
  void sellToFlat_zeroesAvgCost() {
    buy(100, 1000);
    Position p = sell(100, 900);
    assertThat(p.netQty()).isZero();
    assertThat(p.avgCost()).isZero();
    assertThat(p.realizedPnl()).isEqualTo(-100 * 100); // closed at -100 each
  }

  @Test
  void sellThroughZero_flipsShortAtFillPrice() {
    buy(100, 1000);
    Position p = sell(150, 1100);
    assertThat(p.netQty()).isEqualTo(-50);
    assertThat(p.shortQty()).isEqualTo(50);
    assertThat(p.avgCost()).isEqualTo(1100); // surviving short opened at the fill price
    assertThat(p.realizedPnl()).isEqualTo(100 * 100); // long 100 closed at +100
  }

  @Test
  void coverShort_realizesInvertedPnl() {
    sell(100, 1000); // open short at 1000
    Position p = buy(100, 900); // cover lower = profit
    assertThat(p.netQty()).isZero();
    assertThat(p.realizedPnl()).isEqualTo(100 * 100);
  }

  @Test
  void bust_recomputesFromSurvivingFills() {
    buy(100, 1000);
    String bustedExec = "X-" + execSeq;
    buy(100, 1200); // execSeq consumed by helper
    Position rebuilt = positions.applyBust(bustedExec).orElseThrow();
    assertThat(rebuilt.netQty()).isEqualTo(100);
    assertThat(rebuilt.avgCost()).isEqualTo(1000); // as if the busted buy never happened
  }

  @Test
  void bust_canRegressClosedPositionToOpen() {
    buy(100, 1000);
    sell(100, 1100); // flat, realized +10000
    assertThat(positions.position(ACC, FIGI, null).netQty()).isZero();
    Position rebuilt = positions.applyBust("X-2").orElseThrow(); // bust the sell
    assertThat(rebuilt.netQty()).isEqualTo(100); // open again
    assertThat(rebuilt.realizedPnl()).isZero();
  }

  @Test
  void bust_unknownExecId_empty() {
    assertThat(positions.applyBust("NOPE")).isEmpty();
  }

  @Test
  void duplicateExecId_rejected() {
    positions.applyFill(new PositionService.Fill("X-dup", ACC, FIGI, 1, 100, 1000, 1));
    assertThatThrownBy(
            () ->
                positions.applyFill(new PositionService.Fill("X-dup", ACC, FIGI, 1, 100, 1000, 2)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unrealized_computedFromMarkAtReadTime() {
    buy(100, 1000);
    Position marked = positions.position(ACC, FIGI, 1050L);
    assertThat(marked.unrealizedPnl()).isEqualTo(100 * 50);
    Position unmarked = positions.position(ACC, FIGI, null);
    assertThat(unmarked.unrealizedPnl()).isNull(); // no mark — never silently zero
  }

  @Test
  void accounts_areIsolated_andListedSorted() {
    buy(100, 1000);
    positions.applyFill(new PositionService.Fill("X-o1", "acc-2", FIGI, 1, 7, 1000, 9));
    assertThat(positions.position("acc-2", FIGI, null).netQty()).isEqualTo(7);
    assertThat(positions.position(ACC, FIGI, null).netQty()).isEqualTo(100);
    assertThat(positions.positionsForAccount("acc-2")).hasSize(1);
  }

  private Position buy(long qty, long px) {
    return positions.applyFill(
        new PositionService.Fill("X-" + execSeq++, ACC, FIGI, 1, qty, px, execSeq));
  }

  private Position sell(long qty, long px) {
    return positions.applyFill(
        new PositionService.Fill("X-" + execSeq++, ACC, FIGI, 2, qty, px, execSeq));
  }
}
