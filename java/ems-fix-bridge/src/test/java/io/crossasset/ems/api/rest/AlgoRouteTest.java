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
import io.crossasset.ems.fix.algo.AlgoCatalog;
import io.crossasset.ems.fix.algo.AlgoStrategy;
import io.crossasset.ems.instrument.InMemorySecurityMasterService;
import io.crossasset.ems.oms.InMemoryRouteManager;
import io.crossasset.ems.oms.InMemoryStagedOrderManager;
import io.crossasset.ems.validator.LayeredValidatorPipeline;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /api/v1/algos} (task 11.16 wiring): the catalog itself is already unit-tested; this
 * proves the route lists a broker's strategies in definition order, in the shape the ticket needs.
 */
class AlgoRouteTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private RestEdgeBinding binding;
  private long sessionId;

  @BeforeEach
  void setUp() throws Exception {
    InMemoryAaaService aaa = new InMemoryAaaService(new InMemoryAaaEventLog());
    aaa.registerCredential("tok-1", "firm-a", "desk-1", "trader-1", Set.of());
    sessionId =
        ((LogonOutcome.Accepted) aaa.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "tok-1")))
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

    AlgoCatalog catalog = new AlgoCatalog();
    catalog.register(
        new AlgoStrategy(
            "EMSX",
            "VWAP",
            "VWAP",
            List.of(
                new AlgoStrategy.Parameter(
                    "ParticipationRate",
                    AlgoStrategy.ValueType.PERCENTAGE,
                    true,
                    1L,
                    100L,
                    List.of(),
                    "10"))));
    catalog.register(
        new AlgoStrategy(
            "EMSX",
            "TWAP",
            "TWAP",
            List.of(
                new AlgoStrategy.Parameter(
                    "EndTime",
                    AlgoStrategy.ValueType.UTC_TIME,
                    true,
                    null,
                    null,
                    List.of(),
                    null))));
    binding.setAlgoCatalog(catalog);
  }

  private JsonNode getAlgos(String broker) throws Exception {
    RestEdgeBinding.HttpResult result =
        binding.handle(
            "GET",
            "/api/v1/algos",
            Map.of("broker", broker),
            Map.of("x-ems-session", String.valueOf(sessionId)),
            "");
    assertThat(result.status()).isEqualTo(200);
    return mapper.readTree(result.body());
  }

  @Test
  void listsStrategiesInDefinitionOrderWithParameters() throws Exception {
    JsonNode out = getAlgos("EMSX");
    assertThat(out).hasSize(2);
    assertThat(out.get(0).path("wireValue").asText()).isEqualTo("VWAP");
    assertThat(out.get(1).path("wireValue").asText()).isEqualTo("TWAP");

    JsonNode vwapParam = out.get(0).path("parameters").get(0);
    assertThat(vwapParam.path("name").asText()).isEqualTo("ParticipationRate");
    assertThat(vwapParam.path("type").asText()).isEqualTo("PERCENTAGE");
    assertThat(vwapParam.path("required").asBoolean()).isTrue();
    assertThat(vwapParam.path("minValue").asLong()).isEqualTo(1L);
    assertThat(vwapParam.path("maxValue").asLong()).isEqualTo(100L);
    assertThat(vwapParam.path("defaultValue").asText()).isEqualTo("10");
  }

  @Test
  void unknownBrokerReturnsEmptyList() throws Exception {
    assertThat(getAlgos("UNKNOWN-BROKER")).isEmpty();
  }

  @Test
  void missingBrokerParamIs400() throws Exception {
    RestEdgeBinding.HttpResult result =
        binding.handle(
            "GET",
            "/api/v1/algos",
            Map.of(),
            Map.of("x-ems-session", String.valueOf(sessionId)),
            "");
    assertThat(result.status()).isEqualTo(400);
  }

  @Test
  void missingCatalogConfigurationIs404() throws Exception {
    InMemoryAaaService aaa = new InMemoryAaaService(new InMemoryAaaEventLog());
    aaa.registerCredential("tok-2", "firm-a", "desk-1", "trader-2", Set.of());
    long session =
        ((LogonOutcome.Accepted) aaa.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "tok-2")))
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
            "/api/v1/algos",
            Map.of("broker", "EMSX"),
            Map.of("x-ems-session", String.valueOf(session)),
            "");
    assertThat(result.status()).isEqualTo(404);
  }
}
