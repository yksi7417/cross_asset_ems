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
import io.crossasset.ems.api.control.MarketAccessPack;
import io.crossasset.ems.oms.InMemoryRouteManager;
import io.crossasset.ems.oms.InMemoryStagedOrderManager;
import io.crossasset.ems.transport.session.SequenceRecoveryService;
import io.crossasset.ems.validator.LayeredValidatorPipeline;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /api/v1/market-access} (18.5): the 15c3-5 attestation export over the wired pack —
 * session-authenticated, point-in-time evidence, 404 when the edge carries no pack.
 */
class MarketAccessRouteTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private RestEdgeBinding binding;
  private long sessionId;

  @BeforeEach
  void setUp() throws Exception {
    InMemoryAaaService aaa =
        new InMemoryAaaService(
            new InMemoryAaaEventLog(), null, new SequenceRecoveryService(() -> 0L));
    aaa.registerCredential("tok-cco", "firm-a", "desk-1", "cco-1", Set.of());
    InMemoryStagedOrderManager som =
        new InMemoryStagedOrderManager(
            new LayeredValidatorPipeline(
                aaa, new io.crossasset.ems.instrument.InMemorySecurityMasterService(), null));
    SubscriptionRegistry subscriptions = new SubscriptionRegistry();
    ApiSurface api =
        new ApiSurface(
            aaa, som, new InMemoryRouteManager(som), subscriptions, (sid, subId, event) -> {});
    binding = new RestEdgeBinding(aaa, api, subscriptions);
    JsonNode logon =
        mapper.readTree(
            binding
                .handle("POST", "/api/v1/logon", Map.of(), Map.of(), "{\"token\":\"tok-cco\"}")
                .body());
    sessionId = logon.path("sessionId").asLong();
  }

  private static MarketAccessPack onePackControl() {
    MarketAccessPack pack = new MarketAccessPack("firm-a");
    pack.register(
        new MarketAccessPack.ControlMapping(
            "kill-switch",
            "15c3-5(c)",
            "Kill switch",
            MarketAccessPack.ControlStatus.IMPLEMENTED,
            "KillSwitchService",
            null,
            null,
            () -> new ObjectMapper().createObjectNode().put("engaged", 0)));
    return pack;
  }

  @Test
  void exportsTheAttestationSnapshot() throws Exception {
    binding.setMarketAccess(onePackControl(), () -> 9_000L);
    RestEdgeBinding.HttpResult result =
        binding.handle(
            "GET",
            "/api/v1/market-access",
            Map.of(),
            Map.of("x-ems-session", String.valueOf(sessionId)),
            "");
    assertThat(result.status()).isEqualTo(200);
    JsonNode out = mapper.readTree(result.body());
    assertThat(out.path("firm").asText()).isEqualTo("firm-a");
    assertThat(out.path("controls")).hasSize(1);
    assertThat(out.path("controls").get(0).path("controlId").asText()).isEqualTo("kill-switch");
  }

  @Test
  void missingPackIs404() {
    RestEdgeBinding.HttpResult result =
        binding.handle(
            "GET",
            "/api/v1/market-access",
            Map.of(),
            Map.of("x-ems-session", String.valueOf(sessionId)),
            "");
    assertThat(result.status()).isEqualTo(404);
  }

  @Test
  void requiresASession() {
    binding.setMarketAccess(onePackControl(), () -> 9_000L);
    RestEdgeBinding.HttpResult result =
        binding.handle("GET", "/api/v1/market-access", Map.of(), Map.of(), "");
    assertThat(result.status()).isEqualTo(400);
    assertThat(result.body()).contains("X-EMS-Session");
  }
}
