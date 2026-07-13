/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.crossasset.ems.aaa.InMemoryAaaEventLog;
import io.crossasset.ems.aaa.InMemoryAaaService;
import io.crossasset.ems.api.ApiSurface;
import io.crossasset.ems.api.SubscriptionRegistry;
import io.crossasset.ems.instrument.InMemorySecurityMasterService;
import io.crossasset.ems.oms.InMemoryRouteManager;
import io.crossasset.ems.oms.InMemoryStagedOrderManager;
import io.crossasset.ems.pretrade.compliance.ComplianceCheck;
import io.crossasset.ems.pretrade.compliance.ComplianceGate;
import io.crossasset.ems.pretrade.compliance.ComplianceOperation;
import io.crossasset.ems.pretrade.compliance.ComplianceOutcome;
import io.crossasset.ems.pretrade.compliance.OverridePath;
import io.crossasset.ems.pretrade.compliance.OverrideService;
import io.crossasset.ems.transport.session.SequenceRecoveryService;
import io.crossasset.ems.validator.LayeredValidatorPipeline;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Compliance override desk over REST (10.5 wiring): pending blocks listed, four-eyes approval
 * releases, a deny closes, an unqualified signer is refused — signer identity always the session's.
 */
class ComplianceRoutesTest {

  private static final OverridePath FOUR_EYES =
      new OverridePath(Set.of("#compliance-override-restricted-instrument"), 2, 60_000L, true);

  private final ObjectMapper mapper = new ObjectMapper();
  private RestEdgeBinding binding;
  private ComplianceGate gate;
  private long supervisor1;
  private long supervisor2;
  private long clerk;

  @BeforeEach
  void setUp() throws Exception {
    InMemoryAaaService aaa =
        new InMemoryAaaService(
            new InMemoryAaaEventLog(), null, new SequenceRecoveryService(() -> 0L));
    aaa.registerCredential("tok-sup1", "firm-a", "desk-1", "sup-1", Set.of());
    aaa.registerCredential("tok-sup2", "firm-a", "desk-1", "sup-2", Set.of());
    aaa.registerCredential("tok-clerk", "firm-a", "desk-1", "clerk-1", Set.of());
    InMemoryStagedOrderManager som =
        new InMemoryStagedOrderManager(
            new LayeredValidatorPipeline(aaa, new InMemorySecurityMasterService(), null));
    SubscriptionRegistry subscriptions = new SubscriptionRegistry();
    ApiSurface api =
        new ApiSurface(
            aaa, som, new InMemoryRouteManager(som), subscriptions, (sid, subId, event) -> {});
    binding = new RestEdgeBinding(aaa, api, subscriptions);

    ComplianceCheck blockAll =
        new ComplianceCheck() {
          @Override
          public String ruleId() {
            return "lists";
          }

          @Override
          public Optional<Finding> evaluate(ComplianceOperation operation) {
            return Optional.of(
                new Finding(ComplianceOutcome.BLOCK, "instrument_on_restricted_list", FOUR_EYES));
          }
        };
    gate = new ComplianceGate(List.of(blockAll));
    OverrideService overrides =
        new OverrideService(
            gate,
            (firm, desk, user, tag) -> user.startsWith("sup-"), // clerk unqualified
            () -> 9_000L);
    binding.setCompliance(gate, overrides);

    supervisor1 = logon("tok-sup1");
    supervisor2 = logon("tok-sup2");
    clerk = logon("tok-clerk");
  }

  private long logon(String token) throws Exception {
    JsonNode out =
        mapper.readTree(
            binding
                .handle(
                    "POST", "/api/v1/logon", Map.of(), Map.of(), "{\"token\":\"" + token + "\"}")
                .body());
    return out.path("sessionId").asLong();
  }

  private String newBlock() {
    return gate.evaluate(
            new ComplianceOperation(
                ComplianceOperation.Kind.ROUTE,
                7L,
                "firm-a",
                "desk-1",
                "trader-9",
                "O-1",
                "BBG000BLNNH6",
                1,
                100L,
                null,
                "acc-1"))
        .blockId();
  }

  private JsonNode post(String path, long sessionId, String body) throws Exception {
    return mapper.readTree(
        binding
            .handle(
                "POST", path, Map.of(), Map.of("x-ems-session", String.valueOf(sessionId)), body)
            .body());
  }

  @Test
  void pendingBlocksListed() throws Exception {
    String blockId = newBlock();
    JsonNode out =
        mapper.readTree(
            binding
                .handle(
                    "GET",
                    "/api/v1/compliance/blocks",
                    Map.of(),
                    Map.of("x-ems-session", String.valueOf(supervisor1)),
                    "")
                .body());
    assertThat(out.path("blocks")).hasSize(1);
    JsonNode b = out.path("blocks").get(0);
    assertThat(b.path("blockId").asText()).isEqualTo(blockId);
    assertThat(b.path("figi").asText()).isEqualTo("BBG000BLNNH6");
    assertThat(b.path("requiredSignoffs").asInt()).isEqualTo(2);
  }

  @Test
  void fourEyesReleaseOverRest() throws Exception {
    String blockId = newBlock();
    String path = "/api/v1/compliance/blocks/" + blockId + "/approve";

    JsonNode first = post(path, supervisor1, "{\"rationale\":\"checked with desk head\"}");
    assertThat(first.path("status").asText()).isEqualTo("APPROVED");
    assertThat(first.path("signoffsSoFar").asInt()).isEqualTo(1);

    // same signer twice is refused — four-eyes means DISTINCT identities
    JsonNode dup = post(path, supervisor1, "{\"rationale\":\"again\"}");
    assertThat(dup.path("status").asText()).isEqualTo("REJECTED");

    JsonNode second = post(path, supervisor2, "{\"rationale\":\"independent review\"}");
    assertThat(second.path("status").asText()).isEqualTo("RELEASED");
    assertThat(second.path("validUntilMillis").asLong()).isEqualTo(9_000L + 60_000L);
  }

  @Test
  void qualifiedDenierClosesTheBlock() throws Exception {
    String blockId = newBlock();
    JsonNode out =
        post(
            "/api/v1/compliance/blocks/" + blockId + "/deny",
            supervisor1,
            "{\"rationale\":\"trade must not proceed\"}");
    assertThat(out.path("status").asText()).isEqualTo("DENIED");
    assertThat(gate.pendingBlocks()).isEmpty();
  }

  @Test
  void unqualifiedSignerRefused() throws Exception {
    String blockId = newBlock();
    JsonNode out =
        post("/api/v1/compliance/blocks/" + blockId + "/approve", clerk, "{\"rationale\":\"x\"}");
    assertThat(out.path("status").asText()).isEqualTo("REJECTED");
    assertThat(out.path("reason").asText()).contains("tag");
  }
}
