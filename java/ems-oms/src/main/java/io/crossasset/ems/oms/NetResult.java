/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import java.util.List;

/** Result of {@link NettingEngine#net(NettingRequest)}. */
public sealed interface NetResult permits NetResult.Netted, NetResult.Rejected {

  /**
   * Netting applied. {@code groups} holds one entry per collapsed bucket; {@code passthrough} lists
   * candidates left untouched (single-side buckets, do-not-net opt-outs) — same-side candidates
   * sharing a key aggregate instead (arch-aggregation).
   */
  record Netted(List<NetGroup> groups, List<String> passthrough) implements NetResult {}

  /**
   * Whole request refused with no state change (any already-formed groups of this request are
   * unwound); {@code rejectCode} is an EMS-ORD-* / EMS-PRM-* catalog code.
   */
  record Rejected(String requestId, String rejectCode, String message) implements NetResult {}
}
