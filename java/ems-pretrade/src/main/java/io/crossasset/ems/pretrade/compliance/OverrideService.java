/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.pretrade.compliance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Override mechanics over the compliance block store (task 10.5): a block releases only when the
 * required number of <b>distinct</b>, <b>tag-qualified</b> approvers sign off (four-eyes when 2),
 * each with a rationale when the override path demands one; a single qualified denier closes the
 * block immediately. Releases are <b>time-bound</b>: the resuming layer must consume the release
 * while it is valid ({@link #isReleaseValid}) — an expired release means the operation re-runs the
 * gate.
 *
 * <p>Tag qualification delegates to the {@link TagChecker} seam; production wires it to the 5.3
 * three-layer AND-gate ({@code io.crossasset.ems.aaa.permission.TagPermissionEvaluator}) so an
 * override tag must be granted at firm, desk, and user layers. Every decision is recorded on the
 * approval trail for audit.
 */
public final class OverrideService {

  /** Qualification seam: true if the identity holds {@code tag} (5.3 AND-gate in production). */
  @FunctionalInterface
  public interface TagChecker {
    boolean hasTag(String firm, String desk, String user, String tag);
  }

  /** Outcome of an approve/deny call. */
  public sealed interface OverrideResult {
    /** Approval recorded; more sign-offs still required. */
    record Approved(String blockId, int signoffsSoFar, int signoffsRequired)
        implements OverrideResult {}

    /** Final sign-off collected — block released, valid until {@code validUntilMillis}. */
    record Released(PendingBlock block, long validUntilMillis) implements OverrideResult {}

    /** Block denied and closed. */
    record Denied(PendingBlock block) implements OverrideResult {}

    /** Call refused (unqualified, duplicate approver, missing rationale, unknown block, …). */
    record Rejected(String blockId, String reason) implements OverrideResult {}
  }

  private record Approval(String user, String rationale) {}

  private final ComplianceGate gate;
  private final TagChecker tags;
  private final LongSupplier clockMillis;
  private final ConcurrentHashMap<String, List<Approval>> approvals = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Long> releasedUntil = new ConcurrentHashMap<>();

  public OverrideService(ComplianceGate gate, TagChecker tags, LongSupplier clockMillis) {
    this.gate = Objects.requireNonNull(gate, "gate");
    this.tags = Objects.requireNonNull(tags, "tags");
    this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis");
  }

  /** Record one approval; releases the block when the final required sign-off lands. */
  public OverrideResult approve(
      String blockId, String firm, String desk, String user, String rationale) {
    PendingBlock block = pending(blockId);
    if (block == null) {
      return new OverrideResult.Rejected(blockId, "Block unknown or not pending.");
    }
    String disqualification = disqualify(block, firm, desk, user, rationale);
    if (disqualification != null) {
      return new OverrideResult.Rejected(blockId, disqualification);
    }
    List<Approval> trail = approvals.computeIfAbsent(blockId, k -> new ArrayList<>());
    synchronized (trail) {
      if (trail.stream().anyMatch(a -> a.user().equals(user))) {
        return new OverrideResult.Rejected(
            blockId, "Sign-offs must come from distinct identities; " + user + " already signed.");
      }
      trail.add(new Approval(user, rationale));
      int required = block.overridePath().requiredSignoffs();
      if (trail.size() < required) {
        return new OverrideResult.Approved(blockId, trail.size(), required);
      }
      long validUntil = clockMillis.getAsLong() + block.overridePath().expiryMillis();
      PendingBlock released =
          gate.resolveBlock(blockId, PendingBlock.Status.RELEASED, user, rationale).orElseThrow();
      releasedUntil.put(blockId, validUntil);
      return new OverrideResult.Released(released, validUntil);
    }
  }

  /** Deny the block; one qualified denier closes it. */
  public OverrideResult deny(
      String blockId, String firm, String desk, String user, String rationale) {
    PendingBlock block = pending(blockId);
    if (block == null) {
      return new OverrideResult.Rejected(blockId, "Block unknown or not pending.");
    }
    String disqualification = disqualify(block, firm, desk, user, rationale);
    if (disqualification != null) {
      return new OverrideResult.Rejected(blockId, disqualification);
    }
    PendingBlock denied =
        gate.resolveBlock(blockId, PendingBlock.Status.DENIED, user, rationale).orElseThrow();
    return new OverrideResult.Denied(denied);
  }

  /**
   * True while a released block's time-bound window is open. Consumers (the layer resuming the
   * suspended operation) must check this at consumption time; expired releases require
   * re-evaluation through the gate.
   */
  public boolean isReleaseValid(String blockId, long nowMillis) {
    Long until = releasedUntil.get(blockId);
    return until != null && nowMillis < until;
  }

  private @org.jspecify.annotations.Nullable PendingBlock pending(String blockId) {
    Optional<PendingBlock> block = gate.findBlock(blockId);
    return block.isPresent() && block.get().status() == PendingBlock.Status.PENDING
        ? block.get()
        : null;
  }

  private @org.jspecify.annotations.Nullable String disqualify(
      PendingBlock block, String firm, String desk, String user, String rationale) {
    for (String tag : block.overridePath().requiredTags()) {
      if (!tags.hasTag(firm, desk, user, tag)) {
        return "Identity lacks required override tag " + tag + " (EMS-PRM-3001).";
      }
    }
    if (block.overridePath().requiresRationale() && (rationale == null || rationale.isBlank())) {
      return "Override path requires a rationale.";
    }
    return null;
  }
}
