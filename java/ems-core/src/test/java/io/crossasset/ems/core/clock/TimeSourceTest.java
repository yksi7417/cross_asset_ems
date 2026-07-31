/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.core.clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TimeSourceTest {

  @Test
  void fixedTimeSourceReturnsWhatItWasSetTo() {
    FixedTimeSource time = new FixedTimeSource(1_700_000_000_000L);

    assertThat(time.now()).isEqualTo(Timestamp.ofEpochMillis(1_700_000_000_000L));
  }

  @Test
  void nowMicrosIsMillisTimesAThousand() {
    // The conversion lives on the interface because five call sites previously
    // wrote `System.currentTimeMillis() * 1_000L` by hand.
    assertThat(new FixedTimeSource(1_234L).nowMicros()).isEqualTo(1_234_000L);
  }

  @Test
  void fixedTimeSourceAdvances() {
    FixedTimeSource time = new FixedTimeSource(1_000L);
    time.advanceBy(500L);

    assertThat(time.now().epochMillis()).isEqualTo(1_500L);
  }

  @Test
  void fixedTimeSourceRefusesToMoveBackwards() {
    FixedTimeSource time = new FixedTimeSource(1_000L);

    // A test that silently rewinds time asserts about a state the system can
    // never reach.
    assertThatThrownBy(() -> time.set(999L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("backwards");
  }

  @Test
  void fixedTimeSourceRefusesANegativeAdvance() {
    assertThatThrownBy(() -> new FixedTimeSource(0L).advanceBy(-1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void systemTimeSourceIsMonotonicAcrossCalls() {
    long first = SystemTimeSource.INSTANCE.now().epochMillis();
    long second = SystemTimeSource.INSTANCE.now().epochMillis();

    assertThat(second).isGreaterThanOrEqualTo(first);
  }

  @Test
  void simulatedClockIsATimeSource() {
    // Clock extends TimeSource, so anything taking a TimeSource accepts the
    // deterministic clock without a wrapper.
    TimeSource time = new SimulatedClock(0L);

    assertThat(time.nowMicros()).isZero();
  }
}
