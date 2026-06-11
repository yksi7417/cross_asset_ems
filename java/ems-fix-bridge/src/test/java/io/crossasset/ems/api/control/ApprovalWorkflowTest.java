/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.control;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.aaa.CredentialKind;
import io.crossasset.ems.aaa.InMemoryAaaEventLog;
import io.crossasset.ems.aaa.InMemoryAaaService;
import io.crossasset.ems.aaa.LogonCredentials;
import io.crossasset.ems.aaa.LogonOutcome;
import io.crossasset.ems.api.SubscriptionRegistry;
import io.crossasset.ems.api.control.ApprovalWorkflow.Category;
import io.crossasset.ems.api.control.ApprovalWorkflow.Result;
import io.crossasset.ems.api.control.ApprovalWorkflow.Status;
import io.crossasset.ems.pretrade.compliance.ComplianceListService;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Maker-checker tests (task 18.10): a change runs only after a differently-identified, tag-
 * qualified checker approves — author tag (EMS-CFG-1101), approver tag (EMS-CFG-1102), self-
 * approval barred (EMS-CFG-1201) even when the maker holds both tags; reject/withdraw, TTL expiry,
 * failed-apply visibility, and a real restricted-list change under governance.
 */
class ApprovalWorkflowTest {

  private final SubscriptionRegistry registry = new SubscriptionRegistry();
  private final AtomicLong clock = new AtomicLong(1_000L);
  private InMemoryAaaService aaa;
  private ApprovalWorkflow workflow;
  private long makerSession;
  private long checkerSession;
  private long bothTagsSession;
  private long unprivilegedSession;

  @BeforeEach
  void setUp() {
    aaa = new InMemoryAaaService(new InMemoryAaaEventLog());
    aaa.registerCredential(
        "maker",
        "firm",
        "desk",
        "alice",
        Set.of("#config-author-restricted_list", "#config-author-limit"));
    aaa.registerCredential(
        "checker",
        "firm",
        "desk",
        "bob",
        Set.of("#config-approver-restricted_list", "#config-approver-limit"));
    aaa.registerCredential(
        "both",
        "firm",
        "desk",
        "carol",
        Set.of("#config-author-restricted_list", "#config-approver-restricted_list"));
    aaa.registerCredential("pleb", "firm", "desk", "dave", Set.of());
    makerSession = logon("maker");
    checkerSession = logon("checker");
    bothTagsSession = logon("both");
    unprivilegedSession = logon("pleb");
    workflow = new ApprovalWorkflow(aaa, registry, clock::get, 60_000L);
  }

  private long logon(String token) {
    LogonOutcome outcome = aaa.logon(LogonCredentials.fresh(CredentialKind.TOKEN, token));
    return ((LogonOutcome.Accepted) outcome).session().sessionId();
  }

  @Test
  void fourEyes_changeAppliesOnlyAfterDifferentCheckerApproves() {
    ComplianceListService lists = new ComplianceListService();
    Result proposed =
        workflow.propose(
            Category.RESTRICTED_LIST,
            "Restrict BBG000BLNNH6 firm-wide",
            () -> lists.add(ComplianceListService.Kind.RESTRICTED, "firm", "BBG000BLNNH6", 0, null),
            makerSession);
    String proposalId = ((Result.Ok) proposed).proposal().proposalId();

    // Not applied yet.
    assertThat(lists.isActive(ComplianceListService.Kind.RESTRICTED, "firm", "BBG000BLNNH6", 1L))
        .isFalse();
    assertThat(workflow.pending()).hasSize(1);

    Result approved = workflow.approve(proposalId, checkerSession);
    assertThat(((Result.Ok) approved).proposal().status()).isEqualTo(Status.APPLIED);
    assertThat(((Result.Ok) approved).proposal().checkerUser()).isEqualTo("bob");
    assertThat(lists.isActive(ComplianceListService.Kind.RESTRICTED, "firm", "BBG000BLNNH6", 1L))
        .isTrue();
    assertThat(workflow.pending()).isEmpty();
  }

  @Test
  void selfApproval_barred_evenWithBothTags() {
    Result proposed = workflow.propose(Category.RESTRICTED_LIST, "x", () -> {}, bothTagsSession);
    String proposalId = ((Result.Ok) proposed).proposal().proposalId();

    Result self = workflow.approve(proposalId, bothTagsSession);
    assertThat(((Result.Refused) self).code()).isEqualTo("EMS-CFG-1201");
    assertThat(workflow.find(proposalId).orElseThrow().status()).isEqualTo(Status.PENDING);
  }

  @Test
  void tags_gateBothSides() {
    Result refusedMaker =
        workflow.propose(Category.RESTRICTED_LIST, "x", () -> {}, unprivilegedSession);
    assertThat(((Result.Refused) refusedMaker).code()).isEqualTo("EMS-CFG-1101");

    Result proposed = workflow.propose(Category.RESTRICTED_LIST, "x", () -> {}, makerSession);
    String proposalId = ((Result.Ok) proposed).proposal().proposalId();
    Result refusedChecker = workflow.approve(proposalId, unprivilegedSession);
    assertThat(((Result.Refused) refusedChecker).code()).isEqualTo("EMS-CFG-1102");

    // Category tags are scoped: the LIMIT approver tag does not approve RESTRICTED_LIST... and
    // vice versa — carol holds restricted_list tags only.
    Result limitProposal = workflow.propose(Category.LIMIT, "x", () -> {}, makerSession);
    Result wrongScope =
        workflow.approve(((Result.Ok) limitProposal).proposal().proposalId(), bothTagsSession);
    assertThat(((Result.Refused) wrongScope).code()).isEqualTo("EMS-CFG-1102");
  }

  @Test
  void reject_byChecker_andWithdraw_byMaker() {
    String p1 =
        ((Result.Ok) workflow.propose(Category.RESTRICTED_LIST, "a", () -> {}, makerSession))
            .proposal()
            .proposalId();
    String p2 =
        ((Result.Ok) workflow.propose(Category.RESTRICTED_LIST, "b", () -> {}, makerSession))
            .proposal()
            .proposalId();

    Result rejected = workflow.reject(p1, checkerSession, "wrong instrument");
    assertThat(((Result.Ok) rejected).proposal().status()).isEqualTo(Status.REJECTED);

    Result withdrawn = workflow.reject(p2, makerSession, "withdrawn by maker");
    assertThat(((Result.Ok) withdrawn).proposal().status()).isEqualTo(Status.REJECTED);

    Result refused = workflow.reject(p1, unprivilegedSession, "nope");
    assertThat(refused).isInstanceOf(Result.Refused.class);
  }

  @Test
  void ttlExpiry_pendingProposalsLapse() {
    workflow.propose(Category.RESTRICTED_LIST, "stale", () -> {}, makerSession);
    assertThat(workflow.sweepExpired(clock.get() + 59_999)).isZero();
    assertThat(workflow.sweepExpired(clock.get() + 60_000)).isEqualTo(1);
    assertThat(workflow.pending()).isEmpty();
    assertThat(workflow.journal().get(0).status()).isEqualTo(Status.EXPIRED);

    // An expired proposal cannot be approved.
    String id = workflow.journal().get(0).proposalId();
    assertThat(workflow.approve(id, checkerSession)).isInstanceOf(Result.Refused.class);
  }

  @Test
  void failedApply_isVisibleNotSilent() {
    String proposalId =
        ((Result.Ok)
                workflow.propose(
                    Category.RESTRICTED_LIST,
                    "boom",
                    () -> {
                      throw new IllegalStateException("downstream rejected");
                    },
                    makerSession))
            .proposal()
            .proposalId();

    Result approved = workflow.approve(proposalId, checkerSession);
    var proposal = ((Result.Ok) approved).proposal();
    assertThat(proposal.status()).isEqualTo(Status.FAILED);
    assertThat(proposal.resolution()).contains("downstream rejected");
  }

  @Test
  void everyTransition_publishesToTheSupervisorQueue() {
    String proposalId =
        ((Result.Ok) workflow.propose(Category.RESTRICTED_LIST, "x", () -> {}, makerSession))
            .proposal()
            .proposalId();
    workflow.approve(proposalId, checkerSession);

    var events = registry.fetch(ApprovalWorkflow.TOPIC_APPROVALS, 1, 10);
    assertThat(events).hasSize(2);
    assertThat(events.get(0).payload()).contains("PENDING");
    assertThat(events.get(1).payload()).contains("APPLIED");
  }
}
