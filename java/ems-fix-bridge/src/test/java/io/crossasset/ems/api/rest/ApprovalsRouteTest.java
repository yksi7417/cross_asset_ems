/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.crossasset.ems.aaa.CredentialKind;
import io.crossasset.ems.aaa.InMemoryAaaEventLog;
import io.crossasset.ems.aaa.InMemoryAaaService;
import io.crossasset.ems.aaa.LogonCredentials;
import io.crossasset.ems.aaa.LogonOutcome;
import io.crossasset.ems.api.ApiSurface;
import io.crossasset.ems.api.SubscriptionRegistry;
import io.crossasset.ems.api.control.ApprovalWorkflow;
import io.crossasset.ems.instrument.InMemorySecurityMasterService;
import io.crossasset.ems.oms.InMemoryRouteManager;
import io.crossasset.ems.oms.InMemoryStagedOrderManager;
import io.crossasset.ems.validator.LayeredValidatorPipeline;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code /api/v1/approvals} (task 18.10 wiring): the maker-checker mechanics are already
 * unit-tested on {@link ApprovalWorkflow} directly; this proves the REST surface lists the pending
 * queue and that approve/reject route to the right session identity as checker.
 */
class ApprovalsRouteTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private RestEdgeBinding binding;
  private ApprovalWorkflow workflow;
  private long makerSession;
  private long checkerSession;
  private AtomicBoolean applied;

  @BeforeEach
  void setUp() throws Exception {
    InMemoryAaaService aaa = new InMemoryAaaService(new InMemoryAaaEventLog());
    aaa.registerCredential(
        "tok-maker", "firm-a", "desk-1", "maker-1", Set.of("#config-author-restricted_list"));
    aaa.registerCredential(
        "tok-checker", "firm-a", "desk-1", "checker-1", Set.of("#config-approver-restricted_list"));
    makerSession =
        ((LogonOutcome.Accepted)
                aaa.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "tok-maker")))
            .session()
            .sessionId();
    checkerSession =
        ((LogonOutcome.Accepted)
                aaa.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "tok-checker")))
            .session()
            .sessionId();

    InMemoryStagedOrderManager som =
        new InMemoryStagedOrderManager(
            new LayeredValidatorPipeline(aaa, new InMemorySecurityMasterService(), null));
    SubscriptionRegistry subscriptions = new SubscriptionRegistry();
    ApiSurface api =
        new ApiSurface(
            aaa, som, new InMemoryRouteManager(som), subscriptions, (sid, subId, event) -> {});
    binding = new RestEdgeBinding(aaa, api, subscriptions);
    workflow = new ApprovalWorkflow(aaa, subscriptions, () -> 9_000L, 60_000L);
    binding.setApprovals(workflow);
  }

  private String proposeOne() {
    applied = new AtomicBoolean(false);
    var result =
        workflow.propose(
            ApprovalWorkflow.Category.RESTRICTED_LIST,
            "add BBG000BLNNH6 to firm restricted list",
            () -> applied.set(true),
            makerSession);
    return ((ApprovalWorkflow.Result.Ok) result).proposal().proposalId();
  }

  private RestEdgeBinding.HttpResult call(String method, String path, long sessionId, String body)
      throws Exception {
    return binding.handle(
        method, path, Map.of(), Map.of("x-ems-session", String.valueOf(sessionId)), body);
  }

  @Test
  void listsThePendingQueue() throws Exception {
    String proposalId = proposeOne();
    RestEdgeBinding.HttpResult result = call("GET", "/api/v1/approvals", checkerSession, "");
    assertThat(result.status()).isEqualTo(200);
    JsonNode pending = mapper.readTree(result.body()).path("pending");
    assertThat(pending).hasSize(1);
    assertThat(pending.get(0).path("proposalId").asText()).isEqualTo(proposalId);
    assertThat(pending.get(0).path("status").asText()).isEqualTo("PENDING");
  }

  @Test
  void distinctCheckerApprovalRunsTheChange() throws Exception {
    String proposalId = proposeOne();
    RestEdgeBinding.HttpResult result =
        call("POST", "/api/v1/approvals/" + proposalId + "/approve", checkerSession, "");
    assertThat(result.status()).isEqualTo(200);
    assertThat(mapper.readTree(result.body()).path("status").asText()).isEqualTo("APPLIED");
    assertThat(applied).isTrue();
  }

  @Test
  void checkerWithoutTheApproverTagIsRefused() throws Exception {
    String proposalId = proposeOne();
    RestEdgeBinding.HttpResult result =
        call("POST", "/api/v1/approvals/" + proposalId + "/approve", makerSession, "");
    assertThat(result.status()).isEqualTo(409);
    assertThat(mapper.readTree(result.body()).path("code").asText()).isEqualTo("EMS-CFG-1102");
    assertThat(applied).isFalse();
  }

  @Test
  void selfApprovalByATagQualifiedMakerIsStillRefused() throws Exception {
    // maker-1 additionally holds the approver tag here -- the tag check alone would let this
    // through, so this proves the SEPARATE distinct-identity rule (never the same user twice).
    InMemoryAaaService aaa = new InMemoryAaaService(new InMemoryAaaEventLog());
    aaa.registerCredential(
        "tok-both",
        "firm-a",
        "desk-1",
        "maker-1",
        Set.of("#config-author-restricted_list", "#config-approver-restricted_list"));
    long bothSession =
        ((LogonOutcome.Accepted)
                aaa.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "tok-both")))
            .session()
            .sessionId();
    InMemoryStagedOrderManager som =
        new InMemoryStagedOrderManager(
            new LayeredValidatorPipeline(aaa, new InMemorySecurityMasterService(), null));
    SubscriptionRegistry subscriptions = new SubscriptionRegistry();
    RestEdgeBinding qualifiedBinding =
        new RestEdgeBinding(
            aaa,
            new ApiSurface(
                aaa, som, new InMemoryRouteManager(som), subscriptions, (sid, subId, event) -> {}),
            subscriptions);
    ApprovalWorkflow qualifiedWorkflow =
        new ApprovalWorkflow(aaa, subscriptions, () -> 9_000L, 60_000L);
    qualifiedBinding.setApprovals(qualifiedWorkflow);
    var proposed =
        (ApprovalWorkflow.Result.Ok)
            qualifiedWorkflow.propose(
                ApprovalWorkflow.Category.RESTRICTED_LIST, "desc", () -> {}, bothSession);

    RestEdgeBinding.HttpResult result =
        qualifiedBinding.handle(
            "POST",
            "/api/v1/approvals/" + proposed.proposal().proposalId() + "/approve",
            Map.of(),
            Map.of("x-ems-session", String.valueOf(bothSession)),
            "");
    assertThat(result.status()).isEqualTo(409);
    assertThat(mapper.readTree(result.body()).path("code").asText()).isEqualTo("EMS-CFG-1201");
  }

  @Test
  void checkerRejectionRecordsTheReason() throws Exception {
    String proposalId = proposeOne();
    RestEdgeBinding.HttpResult result =
        call(
            "POST",
            "/api/v1/approvals/" + proposalId + "/reject",
            checkerSession,
            "{\"reason\":\"needs desk head sign-off first\"}");
    assertThat(result.status()).isEqualTo(200);
    JsonNode out = mapper.readTree(result.body());
    assertThat(out.path("status").asText()).isEqualTo("REJECTED");
    assertThat(out.path("resolution").asText()).isEqualTo("needs desk head sign-off first");
    assertThat(applied).isFalse();
  }

  @Test
  void rejectWithAnEmptyBodyIsNoReasonNeverA500() throws Exception {
    String proposalId = proposeOne();
    RestEdgeBinding.HttpResult result =
        call("POST", "/api/v1/approvals/" + proposalId + "/reject", checkerSession, "");
    assertThat(result.status()).isEqualTo(200);
    assertThat(mapper.readTree(result.body()).path("status").asText()).isEqualTo("REJECTED");
  }

  @Test
  void rejectWithMalformedJsonIs400NotA500() throws Exception {
    String proposalId = proposeOne();
    RestEdgeBinding.HttpResult result =
        call("POST", "/api/v1/approvals/" + proposalId + "/reject", checkerSession, "{not json");
    assertThat(result.status()).isEqualTo(400);
  }

  @Test
  void missingConfigurationIs404() throws Exception {
    InMemoryAaaService aaa = new InMemoryAaaService(new InMemoryAaaEventLog());
    aaa.registerCredential("tok-x", "firm-a", "desk-1", "x", Set.of());
    long session =
        ((LogonOutcome.Accepted) aaa.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "tok-x")))
            .session()
            .sessionId();
    InMemoryStagedOrderManager som =
        new InMemoryStagedOrderManager(
            new LayeredValidatorPipeline(aaa, new InMemorySecurityMasterService(), null));
    SubscriptionRegistry subscriptions = new SubscriptionRegistry();
    RestEdgeBinding unconfigured =
        new RestEdgeBinding(
            aaa,
            new ApiSurface(
                aaa, som, new InMemoryRouteManager(som), subscriptions, (sid, subId, event) -> {}),
            subscriptions);
    RestEdgeBinding.HttpResult result =
        unconfigured.handle(
            "GET",
            "/api/v1/approvals",
            Map.of(),
            Map.of("x-ems-session", String.valueOf(session)),
            "");
    assertThat(result.status()).isEqualTo(404);
  }
}
