/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.core.journal;

/**
 * Identifier generation for the slice: same seed, same identifiers, in every language.
 *
 * <p>Deliberately a fixed-width counter per prefix, not a seeded PRNG. Identifiers reach the output
 * journal, which the conformance gate compares byte-for-byte across Java, Rust and C++ — and
 * getting three PRNG implementations to agree is a far stronger thing to ask for than getting three
 * counters to agree. The format is {@code <PREFIX>-%010d}; past ten digits the value widens rather
 * than wrapping, because a silently reused identifier is worse than a wider string.
 *
 * <p>Not thread-safe. The slice binary is single-threaded by design — see {@code
 * docs/decisions/0003-shared-schemas-corpus-harness.md}.
 */
public final class DeterministicIds {

  private static final String ORDER_PREFIX = "ORD";
  private static final String ROUTE_PREFIX = "RTE";
  private static final String EXEC_PREFIX = "EXE";

  private final long seed;

  private long orderCounter;
  private long routeCounter;
  private long execCounter;

  /**
   * @param seed starting offset; the first identifier of each kind is {@code seed + 1}
   */
  public DeterministicIds(long seed) {
    if (seed < 0) {
      throw new IllegalArgumentException("seed must not be negative: " + seed);
    }
    this.seed = seed;
    this.orderCounter = seed;
    this.routeCounter = seed;
    this.execCounter = seed;
  }

  public long seed() {
    return seed;
  }

  public String nextOrderId() {
    return format(ORDER_PREFIX, ++orderCounter);
  }

  public String nextRouteId() {
    return format(ROUTE_PREFIX, ++routeCounter);
  }

  public String nextExecId() {
    return format(EXEC_PREFIX, ++execCounter);
  }

  private static String format(String prefix, long value) {
    String digits = Long.toString(value);
    StringBuilder out = new StringBuilder(prefix.length() + 1 + Math.max(10, digits.length()));
    out.append(prefix).append('-');
    for (int i = digits.length(); i < 10; i++) {
      out.append('0');
    }
    return out.append(digits).toString();
  }
}
