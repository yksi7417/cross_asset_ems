/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.allocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure, deterministic split of a fill quantity across account shares.
 *
 * <p>Computes each account's floor share ({@code qty * weightBps / totalWeightBps}) then
 * distributes the integer residual per the {@link RoundingPolicy}. Being a pure function of (qty,
 * shares, policy) it reproduces identical splits under replay (arch-allocation-service.md §
 * Determinism).
 */
final class AllocationSplitter {

  private AllocationSplitter() {}

  /** One account's allocated quantity. */
  record Slice(AccountShare share, long qty) {}

  /**
   * Split {@code totalQty} across {@code shares}. The returned slices are in the same order as the
   * input shares. Slices with zero quantity are retained (a zero allocation is still a decision).
   */
  static List<Slice> split(long totalQty, List<AccountShare> shares, RoundingPolicy rounding) {
    int n = shares.size();
    long totalWeight = shares.stream().mapToLong(AccountShare::weightBps).sum();
    long[] qty = new long[n];
    if (n == 0 || totalWeight <= 0) {
      return List.of();
    }

    long allocated = 0;
    // Track the remainder of each floor division to drive largest-remainder distribution.
    long[] remainder = new long[n];
    for (int i = 0; i < n; i++) {
      long numerator = totalQty * shares.get(i).weightBps();
      qty[i] = numerator / totalWeight;
      remainder[i] = numerator % totalWeight;
      allocated += qty[i];
    }
    long residual = totalQty - allocated;

    if (residual > 0 && rounding != RoundingPolicy.ROUND_DOWN) {
      Integer[] order = orderForResidual(shares, remainder, rounding);
      for (int k = 0; k < residual; k++) {
        qty[order[k % n]]++;
      }
    }

    List<Slice> slices = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      slices.add(new Slice(shares.get(i), qty[i]));
    }
    return slices;
  }

  /** Account indices in the order residual lots should be handed out, per the rounding policy. */
  private static Integer[] orderForResidual(
      List<AccountShare> shares, long[] remainder, RoundingPolicy rounding) {
    Integer[] order = new Integer[shares.size()];
    for (int i = 0; i < order.length; i++) {
      order[i] = i;
    }
    Comparator<Integer> byWeightThenIndex =
        Comparator.<Integer>comparingLong(i -> shares.get(i).weightBps())
            .reversed()
            .thenComparingInt(i -> i);
    Comparator<Integer> comparator =
        switch (rounding) {
          case LARGEST_SHARE_FIRST -> byWeightThenIndex;
          // Largest-remainder method, tie-break by larger weight then lower index.
          default ->
              Comparator.<Integer>comparingLong(i -> remainder[i])
                  .reversed()
                  .thenComparing(byWeightThenIndex);
        };
    java.util.Arrays.sort(order, comparator);
    return order;
  }
}
