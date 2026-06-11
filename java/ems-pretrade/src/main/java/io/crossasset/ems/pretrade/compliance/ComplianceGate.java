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
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;

/**
 * The synchronous pre-trade compliance gate (task 10.1): runs every registered {@link
 * ComplianceCheck} in registration order, records every rule's outcome for audit, and suspends
 * blocked operations into the pending-block store. Distinct from the validator — a BLOCK is not a
 * reject; it waits for a tag-gated, time-bound human override (mechanics in task 10.5).
 *
 * <p>All evaluation continues past the first BLOCK so the audit trail shows the complete rule
 * picture; the decision carries the <i>first</i> blocking rule's block. IDs are deterministic
 * sequences (replay-safe). The asynchronous stream-surveillance half of arch-compliance.md rides
 * the event log and lands with task 12.15.
 */
public final class ComplianceGate {

  private final List<ComplianceCheck> checks;
  private final ConcurrentHashMap<String, PendingBlock> blocks = new ConcurrentHashMap<>();
  private final AtomicLong checkIdSeq = new AtomicLong(1);
  private final AtomicLong blockIdSeq = new AtomicLong(1);

  public ComplianceGate(List<ComplianceCheck> checks) {
    this.checks = List.copyOf(Objects.requireNonNull(checks, "checks"));
  }

  /** Evaluate one operation through every registered rule. Never throws on rule input. */
  public ComplianceDecision evaluate(ComplianceOperation operation) {
    String checkId = "CMP-CHK-" + checkIdSeq.getAndIncrement();
    List<ComplianceDecision.RuleResult> results = new ArrayList<>(checks.size());
    ComplianceCheck.Finding firstBlock = null;
    String firstBlockRuleId = null;
    boolean anyWarn = false;

    for (ComplianceCheck check : checks) {
      Optional<ComplianceCheck.Finding> finding = check.evaluate(operation);
      if (finding.isEmpty()) {
        results.add(new ComplianceDecision.RuleResult(check.ruleId(), ComplianceOutcome.ALLOW, ""));
        continue;
      }
      ComplianceCheck.Finding f = finding.get();
      results.add(new ComplianceDecision.RuleResult(check.ruleId(), f.outcome(), f.rationale()));
      if (f.outcome() == ComplianceOutcome.BLOCK && firstBlock == null) {
        firstBlock = f;
        firstBlockRuleId = check.ruleId();
      } else if (f.outcome() == ComplianceOutcome.WARN) {
        anyWarn = true;
      }
    }

    if (firstBlock != null) {
      String blockId = "CMP-BLK-" + blockIdSeq.getAndIncrement();
      blocks.put(
          blockId,
          new PendingBlock(
              blockId,
              checkId,
              operation,
              firstBlockRuleId,
              firstBlock.rationale(),
              Objects.requireNonNull(firstBlock.overridePath()),
              PendingBlock.Status.PENDING,
              null,
              null));
      return new ComplianceDecision(ComplianceOutcome.BLOCK, checkId, blockId, results);
    }
    return new ComplianceDecision(
        anyWarn ? ComplianceOutcome.WARN : ComplianceOutcome.ALLOW, checkId, null, results);
  }

  /** All blocks currently awaiting review, in block-id order (the compliance officer queue). */
  public List<PendingBlock> pendingBlocks() {
    return blocks.values().stream()
        .filter(b -> b.status() == PendingBlock.Status.PENDING)
        .sorted(java.util.Comparator.comparing(PendingBlock::blockId))
        .toList();
  }

  /** Look up any block (pending or resolved). */
  public Optional<PendingBlock> findBlock(String blockId) {
    return Optional.ofNullable(blocks.get(blockId));
  }

  /**
   * Raw resolution transition — flips a PENDING block to RELEASED or DENIED. The permission checks,
   * sign-off counting, and release expiry around this live in the override service (task 10.5);
   * callers there must verify the override path first. Returns the updated block, or empty if
   * unknown / already resolved.
   */
  public Optional<PendingBlock> resolveBlock(
      String blockId,
      PendingBlock.Status resolution,
      String resolvedBy,
      @Nullable String rationale) {
    if (resolution == PendingBlock.Status.PENDING) {
      throw new IllegalArgumentException("resolution must be RELEASED or DENIED");
    }
    boolean[] transitioned = {false};
    PendingBlock updated =
        blocks.computeIfPresent(
            blockId,
            (id, block) -> {
              if (block.status() != PendingBlock.Status.PENDING) {
                return block; // already resolved — not a fresh transition
              }
              transitioned[0] = true;
              return new PendingBlock(
                  block.blockId(),
                  block.checkId(),
                  block.operation(),
                  block.ruleId(),
                  block.rationale(),
                  block.overridePath(),
                  resolution,
                  resolvedBy,
                  rationale);
            });
    return transitioned[0] ? Optional.ofNullable(updated) : Optional.empty();
  }
}
