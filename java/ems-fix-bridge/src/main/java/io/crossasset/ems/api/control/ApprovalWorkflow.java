/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.crossasset.ems.aaa.AaaService;
import io.crossasset.ems.aaa.Session;
import io.crossasset.ems.api.SubscriptionRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

/**
 * Maker-checker (4-eyes) approvals (task 18.10) on config / limit / restricted-list changes: a
 * change is <em>proposed</em> as a deferred command by a tag-qualified maker, and applies only when
 * a <em>different</em> tag-qualified checker approves — per the catalog's configuration-governance
 * codes: {@code EMS-CFG-1101} (author lacks scope tag), {@code EMS-CFG-1102} (approver lacks scope
 * tag), {@code EMS-CFG-1201} (self-approval not allowed; distinct identities required).
 *
 * <p>Pending proposals expire after a TTL (stale changes must be re-proposed against current
 * state); every transition is journaled and published on {@code control.approvals} so supervisors
 * see the queue live. A command that fails on apply marks the proposal {@code FAILED} with the
 * cause — approved-but-not-applied is never silent.
 */
public final class ApprovalWorkflow {

  /** Approval topic for the supervisor queue. */
  public static final String TOPIC_APPROVALS = "control.approvals";

  /**
   * Governed change categories; tags are {@code #config-author-...} / {@code #config-approver-...}.
   */
  public enum Category {
    CONFIG,
    LIMIT,
    RESTRICTED_LIST
  }

  public enum Status {
    PENDING,
    APPLIED,
    REJECTED,
    EXPIRED,
    FAILED
  }

  /** The deferred change. Runs only on approval; throw to signal failure. */
  @FunctionalInterface
  public interface Change {
    void apply() throws Exception;
  }

  /** One proposal's public state (the command itself is internal). */
  public record Proposal(
      String proposalId,
      Category category,
      String description,
      String makerUser,
      long proposedAtMillis,
      Status status,
      @Nullable String checkerUser,
      @Nullable String resolution,
      long decidedAtMillis) {}

  /** Outcome of propose/approve/reject. */
  public sealed interface Result {
    record Ok(Proposal proposal) implements Result {}

    record Refused(String code, String message) implements Result {}
  }

  private record Pending(Proposal proposal, Change change) {}

  private final AaaService aaa;
  private final SubscriptionRegistry subscriptions;
  private final LongSupplier nowMillis;
  private final long ttlMillis;
  private final ObjectMapper mapper = new ObjectMapper();
  private final Map<String, Pending> proposals = new LinkedHashMap<>();
  private long proposalSeq = 1;

  public ApprovalWorkflow(
      AaaService aaa, SubscriptionRegistry subscriptions, LongSupplier nowMillis, long ttlMillis) {
    this.aaa = Objects.requireNonNull(aaa, "aaa");
    this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
    this.nowMillis = Objects.requireNonNull(nowMillis, "nowMillis");
    if (ttlMillis <= 0) {
      throw new IllegalArgumentException("ttlMillis must be positive");
    }
    this.ttlMillis = ttlMillis;
  }

  static String authorTag(Category category) {
    return "#config-author-" + category.name().toLowerCase(java.util.Locale.ROOT);
  }

  static String approverTag(Category category) {
    return "#config-approver-" + category.name().toLowerCase(java.util.Locale.ROOT);
  }

  /** Propose a change. The command does not run; the proposal enters the checker queue. */
  public synchronized Result propose(
      Category category, String description, Change change, long makerSessionId) {
    Optional<Session> maker = aaa.sessionInfo(makerSessionId);
    if (maker.isEmpty() || !maker.get().identity().effectiveTags().contains(authorTag(category))) {
      return new Result.Refused(
          "EMS-CFG-1101", "Proposing a " + category + " change requires " + authorTag(category));
    }
    Proposal proposal =
        new Proposal(
            "APR-" + proposalSeq++,
            category,
            description,
            maker.get().identity().userId(),
            nowMillis.getAsLong(),
            Status.PENDING,
            null,
            null,
            0);
    proposals.put(proposal.proposalId(), new Pending(proposal, change));
    publish(proposal);
    return new Result.Ok(proposal);
  }

  /** Approve and apply. The checker must hold the approver tag and differ from the maker. */
  public synchronized Result approve(String proposalId, long checkerSessionId) {
    Pending pending = proposals.get(proposalId);
    if (pending == null || pending.proposal().status() != Status.PENDING) {
      return new Result.Refused("EMS-CFG-1001", "No pending proposal " + proposalId);
    }
    Optional<Session> checker = aaa.sessionInfo(checkerSessionId);
    Category category = pending.proposal().category();
    if (checker.isEmpty()
        || !checker.get().identity().effectiveTags().contains(approverTag(category))) {
      return new Result.Refused(
          "EMS-CFG-1102", "Approving a " + category + " change requires " + approverTag(category));
    }
    String checkerUser = checker.get().identity().userId();
    if (checkerUser.equals(pending.proposal().makerUser())) {
      return new Result.Refused(
          "EMS-CFG-1201",
          "Self-approval not allowed; a different qualified approver must action this change.");
    }
    Proposal decided;
    try {
      pending.change().apply();
      decided = decided(pending.proposal(), Status.APPLIED, checkerUser, "applied");
    } catch (Exception e) {
      decided =
          decided(
              pending.proposal(), Status.FAILED, checkerUser, "apply failed: " + e.getMessage());
    }
    proposals.put(proposalId, new Pending(decided, pending.change()));
    publish(decided);
    return new Result.Ok(decided);
  }

  /** Reject with a rationale. Checker tag required; the maker may withdraw their own proposal. */
  public synchronized Result reject(String proposalId, long bySessionId, String reason) {
    Pending pending = proposals.get(proposalId);
    if (pending == null || pending.proposal().status() != Status.PENDING) {
      return new Result.Refused("EMS-CFG-1001", "No pending proposal " + proposalId);
    }
    Optional<Session> by = aaa.sessionInfo(bySessionId);
    if (by.isEmpty()) {
      return new Result.Refused("EMS-CFG-1102", "Unknown session.");
    }
    String byUser = by.get().identity().userId();
    boolean isMakerWithdrawal = byUser.equals(pending.proposal().makerUser());
    boolean isChecker =
        by.get().identity().effectiveTags().contains(approverTag(pending.proposal().category()));
    if (!isMakerWithdrawal && !isChecker) {
      return new Result.Refused(
          "EMS-CFG-1102", "Rejecting requires the approver tag (or be the maker withdrawing).");
    }
    Proposal decided = decided(pending.proposal(), Status.REJECTED, byUser, reason);
    proposals.put(proposalId, new Pending(decided, pending.change()));
    publish(decided);
    return new Result.Ok(decided);
  }

  /** Expire stale pending proposals. Returns how many expired. */
  public synchronized int sweepExpired(long atMillis) {
    int expired = 0;
    for (Pending pending : List.copyOf(proposals.values())) {
      Proposal proposal = pending.proposal();
      if (proposal.status() == Status.PENDING
          && atMillis - proposal.proposedAtMillis() >= ttlMillis) {
        Proposal decided = decided(proposal, Status.EXPIRED, null, "TTL elapsed; re-propose");
        proposals.put(proposal.proposalId(), new Pending(decided, pending.change()));
        publish(decided);
        expired++;
      }
    }
    return expired;
  }

  public synchronized List<Proposal> pending() {
    List<Proposal> out = new ArrayList<>();
    for (Pending pending : proposals.values()) {
      if (pending.proposal().status() == Status.PENDING) {
        out.add(pending.proposal());
      }
    }
    return out;
  }

  public synchronized Optional<Proposal> find(String proposalId) {
    return Optional.ofNullable(proposals.get(proposalId)).map(Pending::proposal);
  }

  /** Full journal (every proposal in every state), oldest first. */
  public synchronized List<Proposal> journal() {
    List<Proposal> out = new ArrayList<>();
    for (Pending pending : proposals.values()) {
      out.add(pending.proposal());
    }
    return out;
  }

  private Proposal decided(
      Proposal proposal, Status status, @Nullable String checkerUser, String resolution) {
    return new Proposal(
        proposal.proposalId(),
        proposal.category(),
        proposal.description(),
        proposal.makerUser(),
        proposal.proposedAtMillis(),
        status,
        checkerUser,
        resolution,
        nowMillis.getAsLong());
  }

  private void publish(Proposal proposal) {
    ObjectNode row = mapper.createObjectNode();
    row.put("proposalId", proposal.proposalId());
    row.put("category", proposal.category().name());
    row.put("description", proposal.description());
    row.put("maker", proposal.makerUser());
    row.put("status", proposal.status().name());
    row.put("checker", proposal.checkerUser());
    row.put("resolution", proposal.resolution());
    row.put("ts", proposal.proposedAtMillis());
    subscriptions.publish(TOPIC_APPROVALS, "ProposalRow", proposal.proposalId(), row.toString());
  }
}
