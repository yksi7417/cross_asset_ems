/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.allocation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for the deterministic {@link AllocationSplitter} rounding logic. */
class AllocationSplitterTest {

  private static AccountShare share(String acct, long bps) {
    return new AccountShare(acct, "PB1", bps);
  }

  private static long total(List<AllocationSplitter.Slice> slices) {
    return slices.stream().mapToLong(AllocationSplitter.Slice::qty).sum();
  }

  @Test
  void equalShares_distributeResidual_splitsExactlyAndConserves() {
    // 5 accounts at 20% each, fill of 17 → 3,3,3,4,4 (per arch-allocation-service example).
    List<AccountShare> shares =
        List.of(
            share("A", 2000),
            share("B", 2000),
            share("C", 2000),
            share("D", 2000),
            share("E", 2000));

    List<AllocationSplitter.Slice> slices =
        AllocationSplitter.split(17, shares, RoundingPolicy.DISTRIBUTE_RESIDUAL);

    assertThat(total(slices)).isEqualTo(17);
    assertThat(slices.stream().map(AllocationSplitter.Slice::qty).sorted().toList())
        .containsExactly(3L, 3L, 3L, 4L, 4L);
  }

  @Test
  void exactDivision_noResidual() {
    List<AccountShare> shares =
        List.of(share("A", 2500), share("B", 2500), share("C", 2500), share("D", 2500));
    List<AllocationSplitter.Slice> slices =
        AllocationSplitter.split(100, shares, RoundingPolicy.DISTRIBUTE_RESIDUAL);
    assertThat(slices.stream().map(AllocationSplitter.Slice::qty).toList())
        .containsExactly(25L, 25L, 25L, 25L);
  }

  @Test
  void largestShareFirst_residualGoesToHighestWeight() {
    List<AccountShare> shares =
        List.of(share("BIG", 5000), share("MID", 3000), share("SMALL", 2000));
    // 11 → floors 5,3,2 (sum 10), residual 1 → LARGEST_SHARE_FIRST → BIG gets it.
    List<AllocationSplitter.Slice> slices =
        AllocationSplitter.split(11, shares, RoundingPolicy.LARGEST_SHARE_FIRST);
    assertThat(slices.stream().map(AllocationSplitter.Slice::qty).toList())
        .containsExactly(6L, 3L, 2L);
    assertThat(total(slices)).isEqualTo(11);
  }

  @Test
  void roundDown_dropsResidual() {
    List<AccountShare> shares =
        List.of(share("BIG", 5000), share("MID", 3000), share("SMALL", 2000));
    List<AllocationSplitter.Slice> slices =
        AllocationSplitter.split(11, shares, RoundingPolicy.ROUND_DOWN);
    // Floors only; the leftover lot stays unallocated.
    assertThat(slices.stream().map(AllocationSplitter.Slice::qty).toList())
        .containsExactly(5L, 3L, 2L);
    assertThat(total(slices)).isEqualTo(10);
  }

  @Test
  void distributeResidual_byLargestRemainder() {
    // floors 4,4,2 (sum 10), residual 1; remainders 4000,4000,2000 → tie A/B, lower index A wins.
    List<AccountShare> shares = List.of(share("A", 4000), share("B", 4000), share("C", 2000));
    List<AllocationSplitter.Slice> slices =
        AllocationSplitter.split(11, shares, RoundingPolicy.DISTRIBUTE_RESIDUAL);
    assertThat(slices.stream().map(AllocationSplitter.Slice::qty).toList())
        .containsExactly(5L, 4L, 2L);
    assertThat(total(slices)).isEqualTo(11);
  }

  @Test
  void emptyShares_yieldsNoSlices() {
    assertThat(AllocationSplitter.split(100, List.of(), RoundingPolicy.DISTRIBUTE_RESIDUAL))
        .isEmpty();
  }
}
