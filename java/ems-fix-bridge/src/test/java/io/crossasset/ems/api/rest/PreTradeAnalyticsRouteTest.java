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
import io.crossasset.ems.instrument.AssetClass;
import io.crossasset.ems.instrument.CurrencyCode;
import io.crossasset.ems.instrument.Fungibility;
import io.crossasset.ems.instrument.InMemorySecurityMasterService;
import io.crossasset.ems.instrument.InstrumentCore;
import io.crossasset.ems.instrument.InstrumentType;
import io.crossasset.ems.instrument.InstrumentVersioned;
import io.crossasset.ems.instrument.LifecycleStatus;
import io.crossasset.ems.instrument.SecurityMasterEvent;
import io.crossasset.ems.instrument.SecurityMasterSnapshot;
import io.crossasset.ems.instrument.SettlementConvention;
import io.crossasset.ems.oms.InMemoryRouteManager;
import io.crossasset.ems.oms.InMemoryStagedOrderManager;
import io.crossasset.ems.pretrade.analytics.PreTradeAnalytics;
import io.crossasset.ems.validator.LayeredValidatorPipeline;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code POST /api/v1/pretrade-analytics/recommend} (task 10.9 wiring): the model registry itself
 * is already unit-tested; this proves the route resolves the instrument's asset class, forwards the
 * market snapshot, and shapes the advisory recommendation as JSON.
 */
class PreTradeAnalyticsRouteTest {

  private static final String EQUITY_FIGI = "BBG000BLNNH6";

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

    InMemorySecurityMasterService secMaster = new InMemorySecurityMasterService();
    InstrumentCore core =
        new InstrumentCore(
            EQUITY_FIGI,
            "IID-1",
            null,
            null,
            AssetClass.EQUITY,
            InstrumentType.COMMON_STOCK,
            "Test Stock",
            "Test Inc.",
            null,
            CurrencyCode.USD,
            "US",
            null,
            Fungibility.FUNGIBLE,
            SettlementConvention.T_PLUS_2,
            0,
            LifecycleStatus.ACTIVE,
            1_000_000L,
            Long.MAX_VALUE,
            1L,
            null,
            1_000_000L,
            1_000_000L);
    secMaster.publish(
        SecurityMasterSnapshot.EMPTY.apply(
            new SecurityMasterEvent.InstrumentCreated(new InstrumentVersioned(core, null), 1L)));

    InMemoryStagedOrderManager som =
        new InMemoryStagedOrderManager(new LayeredValidatorPipeline(aaa, secMaster, null));
    SubscriptionRegistry subscriptions = new SubscriptionRegistry();
    ApiSurface api =
        new ApiSurface(
            aaa, som, new InMemoryRouteManager(som), subscriptions, (sid, subId, event) -> {});
    binding = new RestEdgeBinding(aaa, api, subscriptions, secMaster);

    PreTradeAnalytics analytics = new PreTradeAnalytics();
    analytics.register(new PreTradeAnalytics.SquareRootImpactModel(Set.of(AssetClass.EQUITY)));
    binding.setPreTradeAnalytics(analytics);
  }

  private RestEdgeBinding.HttpResult recommend(String bodyJson) throws Exception {
    return binding.handle(
        "POST",
        "/api/v1/pretrade-analytics/recommend",
        Map.of(),
        Map.of("x-ems-session", String.valueOf(sessionId)),
        bodyJson);
  }

  @Test
  void lowParticipationOrderGetsVwapAdvice() throws Exception {
    RestEdgeBinding.HttpResult result =
        recommend(
            """
            {"figi":"%s","side":1,"qty":1000,"urgency":"LOW","account":"ACC-1",
             "markPx":10000,"adv":1000000,"volatilityBps":50,"spreadBps":2}
            """
                .formatted(EQUITY_FIGI));
    assertThat(result.status()).isEqualTo(200);
    JsonNode out = mapper.readTree(result.body());
    assertThat(out.path("modelId").asText()).isEqualTo("sqrt-impact");
    assertThat(out.path("strategyAdvice").get(0).path("strategy").asText()).isEqualTo("ALGO_VWAP");
    assertThat(out.path("costEstimateBps").isNumber()).isTrue();
  }

  @Test
  void missingMarketDataStillReturnsAManualReviewAdvisoryNeverBlocks() throws Exception {
    RestEdgeBinding.HttpResult result =
        recommend(
            """
            {"figi":"%s","side":1,"qty":1000,"urgency":"LOW","account":"ACC-1"}
            """
                .formatted(EQUITY_FIGI));
    assertThat(result.status()).isEqualTo(200);
    JsonNode out = mapper.readTree(result.body());
    assertThat(out.path("strategyAdvice").get(0).path("strategy").asText())
        .isEqualTo("MANUAL_REVIEW");
    assertThat(out.path("cautions")).hasSize(1);
  }

  @Test
  void invalidUrgencyIs400NotA500() throws Exception {
    RestEdgeBinding.HttpResult result =
        recommend(
            """
            {"figi":"%s","side":1,"qty":100,"urgency":"URGENT","account":"ACC-1"}
            """
                .formatted(EQUITY_FIGI));
    assertThat(result.status()).isEqualTo(400);
  }

  @Test
  void missingSideIs400() throws Exception {
    RestEdgeBinding.HttpResult result =
        recommend(
            """
            {"figi":"%s","qty":100,"account":"ACC-1"}
            """
                .formatted(EQUITY_FIGI));
    assertThat(result.status()).isEqualTo(400);
  }

  @Test
  void nonNumericSideIs400NotSilentlyZero() throws Exception {
    RestEdgeBinding.HttpResult result =
        recommend(
            """
            {"figi":"%s","side":"buy","qty":100,"account":"ACC-1"}
            """
                .formatted(EQUITY_FIGI));
    assertThat(result.status()).isEqualTo(400);
  }

  @Test
  void missingQtyIs400() throws Exception {
    RestEdgeBinding.HttpResult result =
        recommend(
            """
            {"figi":"%s","side":1,"account":"ACC-1"}
            """
                .formatted(EQUITY_FIGI));
    assertThat(result.status()).isEqualTo(400);
  }

  @Test
  void zeroOrNegativeQtyIs400() throws Exception {
    RestEdgeBinding.HttpResult result =
        recommend(
            """
            {"figi":"%s","side":1,"qty":0,"account":"ACC-1"}
            """
                .formatted(EQUITY_FIGI));
    assertThat(result.status()).isEqualTo(400);
  }

  @Test
  void unknownInstrumentIs404() throws Exception {
    RestEdgeBinding.HttpResult result =
        recommend(
            """
            {"figi":"BBG-UNKNOWN","side":1,"qty":100,"account":"ACC-1"}
            """);
    assertThat(result.status()).isEqualTo(404);
  }

  @Test
  void assetClassWithNoRegisteredModelIs404() throws Exception {
    InMemorySecurityMasterService secMaster = new InMemorySecurityMasterService();
    InstrumentCore fx =
        new InstrumentCore(
            "BBG-FX-1",
            "IID-2",
            null,
            null,
            AssetClass.FX,
            InstrumentType.FX_SPOT,
            "EURUSD",
            null,
            null,
            CurrencyCode.USD,
            "US",
            null,
            Fungibility.FUNGIBLE,
            SettlementConvention.T_PLUS_2,
            0,
            LifecycleStatus.ACTIVE,
            1L,
            Long.MAX_VALUE,
            1L,
            null,
            1L,
            1L);
    secMaster.publish(
        SecurityMasterSnapshot.EMPTY.apply(
            new SecurityMasterEvent.InstrumentCreated(new InstrumentVersioned(fx, null), 1L)));
    InMemoryAaaService aaa = new InMemoryAaaService(new InMemoryAaaEventLog());
    aaa.registerCredential("tok-2", "firm-a", "desk-1", "trader-2", Set.of());
    long session =
        ((LogonOutcome.Accepted) aaa.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "tok-2")))
            .session()
            .sessionId();
    InMemoryStagedOrderManager som =
        new InMemoryStagedOrderManager(new LayeredValidatorPipeline(aaa, secMaster, null));
    SubscriptionRegistry subscriptions = new SubscriptionRegistry();
    RestEdgeBinding fxBinding =
        new RestEdgeBinding(
            aaa,
            new ApiSurface(
                aaa, som, new InMemoryRouteManager(som), subscriptions, (sid, subId, event) -> {}),
            subscriptions,
            secMaster);
    PreTradeAnalytics analytics = new PreTradeAnalytics();
    analytics.register(new PreTradeAnalytics.SquareRootImpactModel(Set.of(AssetClass.EQUITY)));
    fxBinding.setPreTradeAnalytics(analytics);

    RestEdgeBinding.HttpResult result =
        fxBinding.handle(
            "POST",
            "/api/v1/pretrade-analytics/recommend",
            Map.of(),
            Map.of("x-ems-session", String.valueOf(session)),
            """
            {"figi":"BBG-FX-1","side":1,"qty":100,"account":"ACC-1"}
            """);
    assertThat(result.status()).isEqualTo(404);
  }

  @Test
  void missingConfigurationIs404() throws Exception {
    InMemoryAaaService aaa = new InMemoryAaaService(new InMemoryAaaEventLog());
    aaa.registerCredential("tok-3", "firm-a", "desk-1", "trader-3", Set.of());
    long session =
        ((LogonOutcome.Accepted) aaa.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "tok-3")))
            .session()
            .sessionId();
    InMemorySecurityMasterService secMaster = new InMemorySecurityMasterService();
    InMemoryStagedOrderManager som =
        new InMemoryStagedOrderManager(new LayeredValidatorPipeline(aaa, secMaster, null));
    SubscriptionRegistry subscriptions = new SubscriptionRegistry();
    RestEdgeBinding unconfigured =
        new RestEdgeBinding(
            aaa,
            new ApiSurface(
                aaa, som, new InMemoryRouteManager(som), subscriptions, (sid, subId, event) -> {}),
            subscriptions,
            secMaster);
    RestEdgeBinding.HttpResult result =
        unconfigured.handle(
            "POST",
            "/api/v1/pretrade-analytics/recommend",
            Map.of(),
            Map.of("x-ems-session", String.valueOf(session)),
            """
            {"figi":"%s","side":1,"qty":100,"account":"ACC-1"}
            """
                .formatted(EQUITY_FIGI));
    assertThat(result.status()).isEqualTo(404);
  }
}
