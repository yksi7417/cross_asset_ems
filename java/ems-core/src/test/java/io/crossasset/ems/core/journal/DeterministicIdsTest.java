/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.core.journal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The exact string form asserted here is a cross-language contract: the Rust and C++ ports must
 * reproduce these identifiers character for character, because they reach the output journal that
 * the conformance gate compares byte-for-byte.
 *
 * <p>A fixed-width counter is used rather than a seeded PRNG precisely so that it is trivially
 * reimplementable in any language — two PRNG implementations agreeing is a much stronger thing to
 * ask for than two counters agreeing.
 */
class DeterministicIdsTest {

  @Test
  void producesTheAgreedStringForm() {
    DeterministicIds ids = new DeterministicIds(0);

    assertThat(ids.nextOrderId()).isEqualTo("ORD-0000000001");
    assertThat(ids.nextRouteId()).isEqualTo("RTE-0000000001");
    assertThat(ids.nextExecId()).isEqualTo("EXE-0000000001");
  }

  @Test
  void countersAreIndependentPerPrefix() {
    DeterministicIds ids = new DeterministicIds(0);

    assertThat(ids.nextOrderId()).isEqualTo("ORD-0000000001");
    assertThat(ids.nextOrderId()).isEqualTo("ORD-0000000002");
    assertThat(ids.nextRouteId()).isEqualTo("RTE-0000000001");
    assertThat(ids.nextOrderId()).isEqualTo("ORD-0000000003");
  }

  @Test
  void sameSeedProducesTheSameSequence() {
    DeterministicIds a = new DeterministicIds(7);
    DeterministicIds b = new DeterministicIds(7);

    for (int i = 0; i < 100; i++) {
      assertThat(a.nextOrderId()).isEqualTo(b.nextOrderId());
    }
  }

  @Test
  void seedOffsetsTheCounter() {
    assertThat(new DeterministicIds(41).nextOrderId()).isEqualTo("ORD-0000000042");
  }

  @Test
  void widthHoldsUntilTheCounterOutgrowsIt() {
    DeterministicIds ids = new DeterministicIds(9_999_999_998L);

    assertThat(ids.nextOrderId()).isEqualTo("ORD-9999999999");
    // Past ten digits the value widens rather than wrapping or truncating —
    // silently reusing an identifier would be far worse than a wider string.
    assertThat(ids.nextOrderId()).isEqualTo("ORD-10000000000");
  }

  @Test
  void negativeSeedIsRejected() {
    assertThatThrownBy(() -> new DeterministicIds(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("seed");
  }
}
