/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import java.util.List;
import java.util.Objects;

/**
 * A batch of candidates to net. {@code account} is the execution book netted parents are staged
 * under. {@code netToZeroAllowed} is the policy switch for buckets whose sides cancel exactly
 * (EMS-ORD-2203 when disallowed) — firm/desk policy cascade per arch-firm-desk-user wires in later;
 * v1 takes the resolved value.
 */
public record NettingRequest(
    String requestId,
    long sessionId,
    List<NettingCandidate> candidates,
    String account,
    boolean netToZeroAllowed) {
  public NettingRequest {
    Objects.requireNonNull(requestId, "requestId");
    Objects.requireNonNull(account, "account");
    candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
  }
}
