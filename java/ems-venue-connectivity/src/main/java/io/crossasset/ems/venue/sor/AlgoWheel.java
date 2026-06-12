/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue.sor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToLongFunction;

/**
 * Algo wheel (task 11.12, [[arch-smart-order-router]] § Algo Wheel): systematic, unbiased
 * broker/algo selection — the standard implementation of the MiFID II RTS 27/28 and US best-ex
 * requirement to demonstrate selection without favoritism. The EMS stores the wheel definition, the
 * per-route selection with its rationale, and the performance inputs: that triple is the audit
 * chain a Best Execution Committee reviews.
 *
 * <p>Selection modes:
 *
 * <ul>
 *   <li><b>ROUND_ROBIN</b> — rotate through eligible buckets;
 *   <li><b>WEIGHTED_RANDOM</b> — weight-proportional, seeded by {@code hash(wheelId, routeId)} —
 *       REPLAY-STABLE: the same route always draws the same bucket, no wall-clock entropy;
 *   <li><b>PERFORMANCE_TIER</b> — weights re-derived from live TCA: each bucket's weight is scaled
 *       by how much cheaper it executes than the worst performer (feed {@code
 *       TcaService.brokerLeague()} in as the slippage supplier) — the 12.14 feedback loop.
 * </ul>
 *
 * <p>Ineligible buckets (missing counterparty tag, size bounds) never win but ARE named in the
 * rationale — selection without favoritism includes showing what was excluded and why.
 */
public final class AlgoWheel implements SorStrategy {

  public enum Mode {
    ROUND_ROBIN,
    WEIGHTED_RANDOM,
    PERFORMANCE_TIER
  }

  /** One wheel bucket: a broker+algo with its base weight and tuned parameters. */
  public record Bucket(String broker, String algo, long weight, Map<String, String> params) {
    public Bucket {
      if (weight <= 0) {
        throw new IllegalArgumentException("bucket weight must be > 0");
      }
      params = Map.copyOf(params);
    }
  }

  /** (intent, bucket) → may this order use this bucket? (cpty tags, notional bounds…) */
  @FunctionalInterface
  public interface Eligibility {
    boolean eligible(RouteIntent intent, Bucket bucket);
  }

  private final String wheelId;
  private final Mode mode;
  private final List<Bucket> buckets;
  private final Eligibility eligibility;
  private final ToLongFunction<String> avgSlippageBp; // broker → TCA avg slippage (perf mode)
  private int roundRobinPointer = 0;

  public AlgoWheel(
      String wheelId,
      Mode mode,
      List<Bucket> buckets,
      Eligibility eligibility,
      ToLongFunction<String> avgSlippageBp) {
    this.wheelId = Objects.requireNonNull(wheelId);
    this.mode = Objects.requireNonNull(mode);
    this.buckets = List.copyOf(buckets);
    this.eligibility = Objects.requireNonNull(eligibility);
    this.avgSlippageBp = Objects.requireNonNull(avgSlippageBp);
    if (buckets.isEmpty()) {
      throw new IllegalArgumentException("a wheel needs buckets");
    }
  }

  /** A wheel with every bucket eligible and no performance feed (ROUND_ROBIN / WEIGHTED_RANDOM). */
  public static AlgoWheel of(String wheelId, Mode mode, List<Bucket> buckets) {
    return new AlgoWheel(wheelId, mode, buckets, (i, b) -> true, broker -> 0L);
  }

  @Override
  public String id() {
    return wheelId;
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public CascadePlan decide(RouteIntent intent, MarketContext context) {
    List<Bucket> eligible = new ArrayList<>();
    List<String> excluded = new ArrayList<>();
    for (Bucket bucket : buckets) {
      if (eligibility.eligible(intent, bucket)) {
        eligible.add(bucket);
      } else {
        excluded.add(bucket.broker() + "/" + bucket.algo());
      }
    }
    if (eligible.isEmpty()) {
      return new CascadePlan(List.of(), "no eligible buckets (excluded: " + excluded + ")");
    }

    Bucket chosen;
    String how;
    switch (mode) {
      case ROUND_ROBIN -> {
        chosen = eligible.get(roundRobinPointer % eligible.size());
        how = "round-robin pointer " + (roundRobinPointer % eligible.size());
        roundRobinPointer++;
      }
      case WEIGHTED_RANDOM -> {
        long total = eligible.stream().mapToLong(Bucket::weight).sum();
        // Replay-stable seed: wheel + route identity, no wall-clock entropy.
        long draw = Math.floorMod((long) (wheelId + "|" + intent.routeId()).hashCode(), total);
        chosen = pickByWeight(eligible, Bucket::weight, draw);
        how = "weighted-random draw " + draw + "/" + total + " (seed wheelId|routeId)";
      }
      case PERFORMANCE_TIER -> {
        // Cheaper brokers earn more weight: scale = 1 + (worstSlippage − bucketSlippage).
        long worst =
            eligible.stream().mapToLong(b -> avgSlippageBp.applyAsLong(b.broker())).max().orElse(0);
        ToLongFunction<Bucket> tuned =
            b -> b.weight() * (1 + Math.max(0, worst - avgSlippageBp.applyAsLong(b.broker())));
        long total = eligible.stream().mapToLong(tuned).sum();
        long draw = Math.floorMod((long) (wheelId + "|" + intent.routeId()).hashCode(), total);
        chosen = pickByWeight(eligible, tuned, draw);
        how =
            "performance-tier draw "
                + draw
                + "/"
                + total
                + " (TCA-scaled weights, worst="
                + worst
                + "bp)";
      }
      default -> throw new IllegalStateException();
    }

    String rationale =
        "wheel "
            + wheelId
            + ": "
            + chosen.broker()
            + "/"
            + chosen.algo()
            + " via "
            + how
            + (excluded.isEmpty() ? "" : "; excluded " + excluded);
    return new CascadePlan(
        List.of(
            new ChildRoute(
                "",
                chosen.broker(),
                chosen.algo(),
                intent.qty(),
                intent.limitPx(),
                0,
                chosen.params())),
        rationale);
  }

  private static Bucket pickByWeight(
      List<Bucket> buckets, ToLongFunction<Bucket> weight, long draw) {
    long cumulative = 0;
    for (Bucket bucket : buckets) {
      cumulative += weight.applyAsLong(bucket);
      if (draw < cumulative) {
        return bucket;
      }
    }
    return buckets.get(buckets.size() - 1);
  }
}
