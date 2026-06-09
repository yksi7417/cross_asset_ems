/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.core.clock;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * A deterministic, single-threaded {@link Clock} implementation for tests and replay.
 *
 * <p>Time does not advance on its own. Callers drive advancement via {@link #advanceTo(Timestamp)}
 * or {@link #advanceBy(long)}. Scheduled callbacks fire in {@code epochMillis} order when the clock
 * is advanced past their scheduled time.
 *
 * <h3>Monotonicity</h3>
 *
 * {@link #now()} is strictly monotonic: it never returns a value less than a prior call. {@link
 * #schedule} and {@link #schedulePeriodic} reject {@code at < now()} to prevent silent
 * backward-time bugs that would corrupt replay determinism.
 *
 * <h3>Replay integration</h3>
 *
 * Event-time-driven advancement (wiring {@link #advanceTo} to the {@code occurred_at} field of each
 * replayed event) is deferred until {@code occurred_at} lands in the event envelope. Until then,
 * replay tests drive the clock manually.
 *
 * <p>This class is intentionally single-threaded. Adding synchronization is YAGNI and would
 * introduce non-determinism in callback ordering.
 *
 * <p>Task 3.6 — sim-clock interface.
 */
public class SimulatedClock implements Clock {

  private long currentEpochMillis;

  /**
   * Stores pending callbacks keyed by scheduled time. {@code TreeMap} provides natural sort order
   * so that callbacks always fire in ascending time order, which is the determinism guarantee. Each
   * time bucket holds a list so that multiple callbacks at the same time fire in registration
   * order.
   */
  private final TreeMap<Long, List<Runnable>> pending = new TreeMap<>();

  /**
   * Creates a simulated clock starting at {@code initialEpochMillis}.
   *
   * @param initialEpochMillis the epoch-millisecond value returned by the first {@link #now()} call
   */
  public SimulatedClock(long initialEpochMillis) {
    this.currentEpochMillis = initialEpochMillis;
  }

  @Override
  public Timestamp now() {
    return Timestamp.ofEpochMillis(currentEpochMillis);
  }

  @Override
  public void schedule(Timestamp at, Runnable callback) {
    if (at.epochMillis() < currentEpochMillis) {
      throw new IllegalArgumentException(
          "Cannot schedule in the past: at="
              + at
              + " now="
              + now()
              + " (replay determinism requires monotonic scheduling)");
    }
    pending.computeIfAbsent(at.epochMillis(), k -> new ArrayList<>()).add(callback);
  }

  @Override
  public void schedulePeriodic(Timestamp start, long periodMillis, Runnable callback) {
    if (periodMillis <= 0) {
      throw new IllegalArgumentException("periodMillis must be positive, got " + periodMillis);
    }
    schedulePeriodic0(start.epochMillis(), periodMillis, callback);
  }

  private void schedulePeriodic0(long at, long periodMillis, Runnable callback) {
    // Guard: only schedule future or present ticks (start may equal now())
    if (at < currentEpochMillis) {
      // periodic ticks before now are silently skipped; advance to the next future tick
      long skipped = (currentEpochMillis - at + periodMillis - 1) / periodMillis;
      at += skipped * periodMillis;
    }
    final long fireAt = at;
    pending
        .computeIfAbsent(fireAt, k -> new ArrayList<>())
        .add(
            () -> {
              callback.run();
              schedulePeriodic0(fireAt + periodMillis, periodMillis, callback);
            });
  }

  /**
   * Advances the clock to {@code target}, firing all scheduled callbacks whose time falls at or
   * before {@code target} in ascending time order.
   *
   * <p>If {@code target} is at or before {@link #now()}, this is a no-op (monotonicity).
   *
   * @param target the new clock time; ignored if not after the current time
   */
  public void advanceTo(Timestamp target) {
    while (true) {
      if (pending.isEmpty()) {
        break;
      }
      long nextScheduled = pending.firstKey();
      if (nextScheduled > target.epochMillis()) {
        break;
      }
      // Advance the clock exactly to the next callback time before firing it.
      // This ensures that if a callback calls now(), it sees the correct time.
      currentEpochMillis = nextScheduled;
      List<Runnable> callbacks = new ArrayList<>(pending.remove(nextScheduled));
      for (Runnable cb : callbacks) {
        cb.run(); // may call schedulePeriodic0, which adds to pending
      }
    }
    // Advance to target even if no callbacks fired at this time
    if (target.epochMillis() > currentEpochMillis) {
      currentEpochMillis = target.epochMillis();
    }
  }

  /**
   * Advances the clock by {@code millis} milliseconds relative to the current time.
   *
   * @param millis the number of milliseconds to advance; must be non-negative
   * @throws IllegalArgumentException if {@code millis < 0}
   */
  public void advanceBy(long millis) {
    if (millis < 0) {
      throw new IllegalArgumentException("Cannot advance by a negative amount: " + millis);
    }
    advanceTo(Timestamp.ofEpochMillis(currentEpochMillis + millis));
  }

  /** Returns the number of callbacks currently pending (for diagnostics and test assertions). */
  public int pendingCount() {
    return pending.values().stream().mapToInt(List::size).sum();
  }
}
