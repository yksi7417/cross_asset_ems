/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.compliance;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;

/**
 * Fat-finger check (task 10.2, [[arch-compliance]] § Fat-finger check): {@code 12.50} typed as
 * {@code 125.0}, qty {@code 100,000} as {@code 1,000,000} — pre-trade compliance catches the
 * exploded order before it reaches a venue. Three independent trips, each a BLOCK with the {@code
 * #compliance-override-fat-finger} supervisor path (a desk supervisor can release after eyeballing
 * — this is the compliance block tier, not the validator's hard reject):
 *
 * <ol>
 *   <li><b>Notional</b> — qty × effective price over the desk threshold. NETTED-vs-UNNETTED: a
 *       risk-REDUCING order (sells when long, buys when short, per the position supplier) gets
 *       {@code nettingReliefMultiple}× headroom — blocking a trader closing a fat position is how
 *       fat positions stay fat.
 *   <li><b>Price deviation</b> — limit px more than {@code maxDeviationBp} away from the live
 *       reference (the 9.5 benchmark mid). Market orders skip this trip.
 *   <li><b>No reference</b> — when policy demands a reference price and none exists, the order
 *       blocks rather than sails unchecked ({@code #compliance-override-no-ref}).
 * </ol>
 *
 * <p>This is the LIVE control behind the 15c3-5 pack's "erroneous order prevention" line (carried
 * DEFERRED since the Phase 9 deferral; 9.5's BenchmarkService closed the gap).
 */
public final class FatFingerCheck implements ComplianceCheck {

  /**
   * @param maxNotional fixed-point 1e4 notional ceiling per order (desk-level config)
   * @param maxDeviationBp limit-price deviation band vs the reference mid
   * @param nettingReliefMultiple notional headroom multiple for risk-reducing orders (≥1)
   * @param blockOnNoReference block when no reference price exists (true for equities; false for
   *     instruments quoted off-feed)
   */
  public record Policy(
      long maxNotional,
      long maxDeviationBp,
      long nettingReliefMultiple,
      boolean blockOnNoReference) {
    public Policy {
      if (nettingReliefMultiple < 1) {
        throw new IllegalArgumentException("nettingReliefMultiple must be >= 1");
      }
    }
  }

  private static final OverridePath FAT_FINGER_OVERRIDE =
      new OverridePath(Set.of("#compliance-override-fat-finger"), 1, 3_600_000L, true);
  private static final OverridePath NO_REF_OVERRIDE =
      new OverridePath(Set.of("#compliance-override-no-ref"), 1, 3_600_000L, true);

  /** figi → live reference price (the 9.5 benchmark mid); empty = no reference. */
  @FunctionalInterface
  public interface ReferencePrice {
    OptionalLong mid(String figi);
  }

  /** (account, figi) → signed net position (positive long, negative short); 0 = flat/unknown. */
  @FunctionalInterface
  public interface NetPosition {
    long netQty(String account, String figi);
  }

  private final Policy policy;
  private final ReferencePrice reference;
  private final NetPosition positions;
  private final Function<String, Long> contractMultiplier;

  public FatFingerCheck(Policy policy, ReferencePrice reference, NetPosition positions) {
    this(policy, reference, positions, figi -> 1L);
  }

  /**
   * @param contractMultiplier per-instrument multiplier (futures); 1 for cash instruments
   */
  public FatFingerCheck(
      Policy policy,
      ReferencePrice reference,
      NetPosition positions,
      Function<String, Long> contractMultiplier) {
    this.policy = Objects.requireNonNull(policy, "policy");
    this.reference = Objects.requireNonNull(reference, "reference");
    this.positions = Objects.requireNonNull(positions, "positions");
    this.contractMultiplier = Objects.requireNonNull(contractMultiplier, "contractMultiplier");
  }

  @Override
  public String ruleId() {
    return "fat-finger";
  }

  @Override
  public Optional<Finding> evaluate(ComplianceOperation op) {
    if (op.kind() != ComplianceOperation.Kind.STAGE
        && op.kind() != ComplianceOperation.Kind.AMEND) {
      return Optional.empty(); // sizing was vetted at stage/amend; ready/route don't resize
    }
    OptionalLong referenceMid = reference.mid(op.figi());

    if (referenceMid.isEmpty() && op.price() == null) {
      // Market order with no reference at all: nothing to band against.
      return policy.blockOnNoReference()
          ? Optional.of(
              new Finding(
                  ComplianceOutcome.BLOCK,
                  "no reference price for " + op.figi() + " (market order unchecked)",
                  NO_REF_OVERRIDE))
          : Optional.empty();
    }

    // 1. Price deviation vs reference (limit orders with a reference).
    if (op.price() != null && referenceMid.isPresent()) {
      long mid = referenceMid.getAsLong();
      long deviationBp = Math.abs(op.price() - mid) * 10_000 / Math.max(1, mid);
      if (deviationBp > policy.maxDeviationBp()) {
        return Optional.of(
            new Finding(
                ComplianceOutcome.BLOCK,
                "limit "
                    + op.price()
                    + " deviates "
                    + deviationBp
                    + "bp from reference "
                    + mid
                    + " (max "
                    + policy.maxDeviationBp()
                    + "bp)",
                FAT_FINGER_OVERRIDE));
      }
    }

    // 2. Notional ceiling — with netting relief for risk-reducing orders.
    long effectivePx = op.price() != null ? op.price() : referenceMid.orElse(0);
    long notional = op.qty() * effectivePx * contractMultiplier.apply(op.figi());
    long net = positions.netQty(op.account(), op.figi());
    boolean riskReducing = (op.side() == 1 && net < 0) || (op.side() != 1 && net > 0);
    long threshold =
        riskReducing ? policy.maxNotional() * policy.nettingReliefMultiple() : policy.maxNotional();
    if (notional > threshold) {
      return Optional.of(
          new Finding(
              ComplianceOutcome.BLOCK,
              "notional "
                  + notional
                  + " exceeds fat-finger threshold "
                  + threshold
                  + (riskReducing ? " (netting relief applied)" : "")
                  + " — qty "
                  + op.qty()
                  + " @ "
                  + effectivePx,
              FAT_FINGER_OVERRIDE));
    }
    return Optional.empty();
  }
}
