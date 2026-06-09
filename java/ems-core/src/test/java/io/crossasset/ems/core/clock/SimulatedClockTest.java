/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.core.clock;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SimulatedClock}: determinism, monotonicity, out-of-order registration, periodic
 * scheduling, and golden-replay equivalence.
 *
 * <p>The discriminating test schedules callbacks in REVERSE time order and asserts they fire in
 * FORWARD time order — this is what separates a correct sorted-schedule implementation from a naive
 * list-based one.
 *
 * <p>Task 3.6 — sim-clock interface.
 */
class SimulatedClockTest {

  private static final long T0 = 1_000L;

  // ── now / advanceTo ──────────────────────────────────────────────────────────

  @Test
  void now_returnsInitialTime() {
    SimulatedClock clock = new SimulatedClock(T0);
    assertEquals(Timestamp.ofEpochMillis(T0), clock.now());
  }

  @Test
  void advanceTo_updatesNow() {
    SimulatedClock clock = new SimulatedClock(T0);
    clock.advanceTo(Timestamp.ofEpochMillis(T0 + 500));
    assertEquals(T0 + 500, clock.now().epochMillis());
  }

  @Test
  void advanceBy_updatesNow() {
    SimulatedClock clock = new SimulatedClock(T0);
    clock.advanceBy(200);
    assertEquals(T0 + 200, clock.now().epochMillis());
  }

  // ── monotonicity ─────────────────────────────────────────────────────────────

  @Test
  void advanceTo_pastTarget_isNoOp() {
    SimulatedClock clock = new SimulatedClock(T0);
    clock.advanceTo(Timestamp.ofEpochMillis(T0 + 100));
    clock.advanceTo(Timestamp.ofEpochMillis(T0)); // earlier than current — no-op
    assertEquals(
        T0 + 100, clock.now().epochMillis(), "advanceTo past target must not move clock backward");
  }

  @Test
  void schedule_beforeNow_throws() {
    SimulatedClock clock = new SimulatedClock(T0);
    clock.advanceBy(100);
    assertThrows(
        IllegalArgumentException.class,
        () -> clock.schedule(Timestamp.ofEpochMillis(T0), () -> {}),
        "Scheduling in the past must be rejected to preserve monotonicity");
  }

  @Test
  void advanceBy_negativeAmount_throws() {
    SimulatedClock clock = new SimulatedClock(T0);
    assertThrows(IllegalArgumentException.class, () -> clock.advanceBy(-1));
  }

  // ── deterministic ordering ────────────────────────────────────────────────────

  /**
   * Discriminating test: schedules three callbacks in REVERSE time order and asserts they fire in
   * FORWARD time order. A naive list-based structure would fire them in registration order
   * (backward), failing this test.
   */
  @Test
  void schedule_outOfTimeOrder_firesInTimeOrder() {
    SimulatedClock clock = new SimulatedClock(T0);
    List<Long> fired = new ArrayList<>();

    // Register in reverse order: T0+300, T0+100, T0+200
    clock.schedule(Timestamp.ofEpochMillis(T0 + 300), () -> fired.add(T0 + 300L));
    clock.schedule(Timestamp.ofEpochMillis(T0 + 100), () -> fired.add(T0 + 100L));
    clock.schedule(Timestamp.ofEpochMillis(T0 + 200), () -> fired.add(T0 + 200L));

    clock.advanceTo(Timestamp.ofEpochMillis(T0 + 300));

    assertEquals(
        List.of(T0 + 100, T0 + 200, T0 + 300),
        fired,
        "Callbacks must fire in ascending time order regardless of registration order");
  }

  // ── callback fires at correct clock time ──────────────────────────────────────

  @Test
  void schedule_callbackSeesCorrectNow() {
    SimulatedClock clock = new SimulatedClock(T0);
    long[] nowAtFire = {-1L};
    clock.schedule(
        Timestamp.ofEpochMillis(T0 + 50), () -> nowAtFire[0] = clock.now().epochMillis());

    clock.advanceTo(Timestamp.ofEpochMillis(T0 + 100));

    assertEquals(T0 + 50, nowAtFire[0], "now() inside callback must reflect the scheduled time");
  }

  // ── partial advance ──────────────────────────────────────────────────────────

  @Test
  void advanceTo_partialWindow_onlyFiresCallbacksInRange() {
    SimulatedClock clock = new SimulatedClock(T0);
    List<Long> fired = new ArrayList<>();

    clock.schedule(Timestamp.ofEpochMillis(T0 + 100), () -> fired.add(T0 + 100L));
    clock.schedule(Timestamp.ofEpochMillis(T0 + 200), () -> fired.add(T0 + 200L));
    clock.schedule(Timestamp.ofEpochMillis(T0 + 300), () -> fired.add(T0 + 300L));

    clock.advanceTo(Timestamp.ofEpochMillis(T0 + 200)); // only T+100 and T+200

    assertEquals(List.of(T0 + 100, T0 + 200), fired);
    assertEquals(T0 + 200, clock.now().epochMillis());

    // Advance the rest
    clock.advanceTo(Timestamp.ofEpochMillis(T0 + 300));
    assertEquals(List.of(T0 + 100, T0 + 200, T0 + 300), fired);
  }

  // ── periodic scheduling ──────────────────────────────────────────────────────

  @Test
  void schedulePeriodic_firesAtEachPeriod() {
    SimulatedClock clock = new SimulatedClock(T0);
    List<Long> fired = new ArrayList<>();

    clock.schedulePeriodic(
        Timestamp.ofEpochMillis(T0 + 100), 50, () -> fired.add(clock.now().epochMillis()));

    clock.advanceTo(Timestamp.ofEpochMillis(T0 + 300));

    // Fires at: T+100, T+150, T+200, T+250, T+300
    assertEquals(List.of(T0 + 100, T0 + 150, T0 + 200, T0 + 250, T0 + 300), fired);
  }

  @Test
  void schedulePeriodic_zeroPeriod_throws() {
    SimulatedClock clock = new SimulatedClock(T0);
    assertThrows(
        IllegalArgumentException.class,
        () -> clock.schedulePeriodic(Timestamp.ofEpochMillis(T0), 0, () -> {}));
  }

  @Test
  void schedulePeriodic_negativePeriod_throws() {
    SimulatedClock clock = new SimulatedClock(T0);
    assertThrows(
        IllegalArgumentException.class,
        () -> clock.schedulePeriodic(Timestamp.ofEpochMillis(T0), -100, () -> {}));
  }

  // ── golden-replay equivalence ─────────────────────────────────────────────────

  /**
   * Golden-replay guarantee for clocks: two SimulatedClocks with the same initial state, same
   * schedules, and same advance sequence must produce identical firing sequences — the prerequisite
   * for byte-identical replay.
   */
  @Test
  void golden_twoClocksSameSetup_producesIdenticalFiringSequence() {
    List<Long> fired1 = new ArrayList<>();
    List<Long> fired2 = new ArrayList<>();

    for (List<Long> fired : List.of(fired1, fired2)) {
      SimulatedClock clock = new SimulatedClock(T0);
      clock.schedule(Timestamp.ofEpochMillis(T0 + 200), () -> fired.add(T0 + 200L));
      clock.schedule(Timestamp.ofEpochMillis(T0 + 100), () -> fired.add(T0 + 100L));
      clock.schedulePeriodic(
          Timestamp.ofEpochMillis(T0 + 50), 150, () -> fired.add(-1L)); // -1 sentinel for periodic
      clock.advanceTo(Timestamp.ofEpochMillis(T0 + 350));
    }

    assertEquals(
        fired1,
        fired2,
        "Two SimulatedClocks with identical setup must produce identical firing sequences");
  }

  // ── pending count ────────────────────────────────────────────────────────────

  @Test
  void pendingCount_reflectsScheduledAndFiredCallbacks() {
    SimulatedClock clock = new SimulatedClock(T0);
    assertEquals(0, clock.pendingCount());

    clock.schedule(Timestamp.ofEpochMillis(T0 + 100), () -> {});
    clock.schedule(Timestamp.ofEpochMillis(T0 + 200), () -> {});
    assertEquals(2, clock.pendingCount());

    clock.advanceTo(Timestamp.ofEpochMillis(T0 + 100));
    assertEquals(1, clock.pendingCount(), "Fired callback must be removed from pending");

    clock.advanceTo(Timestamp.ofEpochMillis(T0 + 200));
    assertEquals(0, clock.pendingCount());
  }
}
