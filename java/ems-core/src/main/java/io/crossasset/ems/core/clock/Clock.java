/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.core.clock;

/**
 * The sole source of time for all business logic.
 *
 * <p>Components must <em>never</em> call {@code System.currentTimeMillis()} or {@code
 * System.nanoTime()} directly. They receive a {@code Clock} at construction and use it exclusively
 * for all temporal operations. This allows tests and the replay engine to substitute a {@link
 * SimulatedClock} that advances deterministically.
 *
 * <h3>Implementations</h3>
 *
 * <ul>
 *   <li>{@link SimulatedClock} — deterministic, manually driven; used by tests and replay.
 *   <li>A live {@code SystemClock} (wall-clock aligned with offset corrections) belongs to a later
 *       phase per {@code arch-time-replay-server}.
 * </ul>
 *
 * <p>Task 3.6 — sim-clock interface.
 */
public interface Clock {

  /**
   * Returns the current time. Monotonic in live mode; advanced explicitly in simulated mode. Never
   * returns a value less than a prior call on the same instance.
   */
  Timestamp now();

  /**
   * Schedules a one-shot callback to fire when the clock reaches {@code at}.
   *
   * <p>In {@link SimulatedClock}, the callback fires when the clock is advanced to or past {@code
   * at}. Multiple callbacks at the same time fire in registration order.
   *
   * @param at the time at which to fire; must not be before {@link #now()} (monotonicity)
   * @param callback the action to execute at the scheduled time
   * @throws IllegalArgumentException if {@code at} is before the current time
   */
  void schedule(Timestamp at, Runnable callback);

  /**
   * Schedules a recurring callback, firing at {@code start} and then every {@code periodMillis}
   * thereafter.
   *
   * <p>The first fire is at {@code start}; subsequent fires at {@code start + n * periodMillis}. If
   * advancing time skips several periods, all missed fires are triggered in order.
   *
   * @param start the time of the first fire; must not be before {@link #now()}
   * @param periodMillis period between fires; must be positive
   * @param callback the action to execute on each tick
   * @throws IllegalArgumentException if {@code start} is before now or {@code periodMillis <= 0}
   */
  void schedulePeriodic(Timestamp start, long periodMillis, Runnable callback);
}
