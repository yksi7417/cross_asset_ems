/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.core.clock;

/**
 * A {@link TimeSource} that returns whatever it was last set to.
 *
 * <p>For tests that need to pin or step time without the scheduling machinery of {@link
 * SimulatedClock}. Reach for {@code SimulatedClock} when callbacks matter; reach for this when they
 * do not.
 *
 * <p>Monotonicity is enforced: {@link #set} and {@link #advanceBy} reject a move backwards. A test
 * that silently rewinds time produces a passing assertion about a state the system can never reach.
 */
public final class FixedTimeSource implements TimeSource {

  private long epochMillis;

  /** Starts at epoch 0. */
  public FixedTimeSource() {
    this(0L);
  }

  public FixedTimeSource(long epochMillis) {
    if (epochMillis < 0) {
      throw new IllegalArgumentException("epochMillis must not be negative: " + epochMillis);
    }
    this.epochMillis = epochMillis;
  }

  @Override
  public Timestamp now() {
    return Timestamp.ofEpochMillis(epochMillis);
  }

  /** Moves time to {@code millis}. Must not move backwards. */
  public void set(long millis) {
    if (millis < epochMillis) {
      throw new IllegalArgumentException(
          "time must not move backwards: " + epochMillis + " -> " + millis);
    }
    epochMillis = millis;
  }

  /** Moves time forward by {@code millis}. Must not be negative. */
  public void advanceBy(long millis) {
    if (millis < 0) {
      throw new IllegalArgumentException("cannot advance by a negative amount: " + millis);
    }
    epochMillis += millis;
  }
}
