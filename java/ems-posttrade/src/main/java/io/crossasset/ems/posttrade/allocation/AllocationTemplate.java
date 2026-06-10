/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.allocation;

import java.util.List;
import java.util.Objects;

/**
 * The allocation instructions pinned onto an order at stage time. The version is captured so a
 * retroactive edit of the template does not change historical allocations (replay determinism, per
 * arch-allocation-service.md § Determinism/replay).
 *
 * <p>A {@code deferred} template represents the block-now-allocate-later workflow: fills park until
 * an operator supplies the real template via {@code setAllocationTemplate}.
 */
public record AllocationTemplate(
    String templateId,
    long version,
    AllocationPolicy policy,
    RoundingPolicy rounding,
    List<AccountShare> shares,
    boolean deferred) {

  public AllocationTemplate {
    Objects.requireNonNull(templateId, "templateId");
    Objects.requireNonNull(policy, "policy");
    Objects.requireNonNull(rounding, "rounding");
    shares = shares == null ? List.of() : List.copyOf(shares);
  }

  /** A concrete (non-deferred) template. */
  public static AllocationTemplate of(
      String templateId,
      long version,
      AllocationPolicy policy,
      RoundingPolicy rounding,
      List<AccountShare> shares) {
    return new AllocationTemplate(templateId, version, policy, rounding, shares, false);
  }

  /** A deferred (block-now-allocate-later) template with no shares yet. */
  public static AllocationTemplate deferred(String templateId) {
    return new AllocationTemplate(
        templateId,
        0L,
        AllocationPolicy.PRO_RATA,
        RoundingPolicy.DISTRIBUTE_RESIDUAL,
        List.of(),
        true);
  }

  /** Total of all account weights in basis points; should be 10000 for a well-formed template. */
  public long totalWeightBps() {
    return shares.stream().mapToLong(AccountShare::weightBps).sum();
  }
}
