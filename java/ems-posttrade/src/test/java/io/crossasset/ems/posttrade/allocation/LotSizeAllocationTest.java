/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.allocation;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.posttrade.allocation.AllocationEvent.AllocationApplied;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for asset-class allocation precision / lot sizing (16.2). */
class LotSizeAllocationTest {

  private static AccountShare share(String acct, long bps) {
    return new AccountShare(acct, "PB1", bps);
  }

  private static long total(List<AllocationSplitter.Slice> slices) {
    return slices.stream().mapToLong(AllocationSplitter.Slice::qty).sum();
  }

  @Test
  void lotSizeOne_isPlainSplit() {
    List<AccountShare> shares = List.of(share("A", 6000), share("B", 4000));
    assertThat(AllocationSplitter.split(100, shares, RoundingPolicy.DISTRIBUTE_RESIDUAL, 1))
        .isEqualTo(AllocationSplitter.split(100, shares, RoundingPolicy.DISTRIBUTE_RESIDUAL));
  }

  @Test
  void fxMinNotional_allocatesInWholeLots_oddLotUnallocated() {
    // 95k split 50/50 at 10k lots → 9 lots → 5/4 → 50k/40k; the 5k odd lot stays unallocated.
    List<AccountShare> shares = List.of(share("A", 5000), share("B", 5000));
    List<AllocationSplitter.Slice> slices =
        AllocationSplitter.split(95_000, shares, RoundingPolicy.DISTRIBUTE_RESIDUAL, 10_000);

    assertThat(slices).allSatisfy(s -> assertThat(s.qty() % 10_000).isZero());
    assertThat(total(slices)).isEqualTo(90_000); // 5k odd lot dropped
    assertThat(slices.stream().map(AllocationSplitter.Slice::qty).sorted().toList())
        .containsExactly(40_000L, 50_000L);
  }

  @Test
  void bondDenomination_allocatesInThousands() {
    List<AccountShare> shares = List.of(share("A", 6000), share("B", 4000));
    List<AllocationSplitter.Slice> slices =
        AllocationSplitter.split(1_000_000, shares, RoundingPolicy.DISTRIBUTE_RESIDUAL, 1_000);
    assertThat(slices.stream().map(AllocationSplitter.Slice::qty).toList())
        .containsExactly(600_000L, 400_000L);
    assertThat(slices).allSatisfy(s -> assertThat(s.qty() % 1_000).isZero());
  }

  @Test
  void service_respectsTemplateLotSize() {
    AllocationService svc = new InMemoryAllocationService();
    AllocationTemplate fxTemplate =
        AllocationTemplate.of(
            "FX-TPL",
            1L,
            AllocationPolicy.PRO_RATA,
            RoundingPolicy.DISTRIBUTE_RESIDUAL,
            List.of(share("A", 5000), share("B", 5000)),
            10_000);

    List<AllocationEvent> events =
        svc.allocate(new Fill("F1", "ORD-1", "RTE-1", 95_000, 11_000), fxTemplate);

    List<AllocationApplied> applied =
        events.stream()
            .filter(e -> e instanceof AllocationApplied)
            .map(e -> (AllocationApplied) e)
            .toList();
    assertThat(applied).allSatisfy(a -> assertThat(a.qty() % 10_000).isZero());
    assertThat(applied.stream().mapToLong(AllocationApplied::qty).sum()).isEqualTo(90_000);
  }
}
