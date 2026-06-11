/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.aaa.CredentialKind;
import io.crossasset.ems.aaa.InMemoryAaaEventLog;
import io.crossasset.ems.aaa.InMemoryAaaService;
import io.crossasset.ems.aaa.LogonCredentials;
import io.crossasset.ems.aaa.LogonOutcome;
import io.crossasset.ems.api.ApiItem;
import io.crossasset.ems.api.ApiOperation;
import io.crossasset.ems.api.ApiRequest;
import io.crossasset.ems.api.ApiResponse;
import io.crossasset.ems.api.ApiSurface;
import io.crossasset.ems.api.SubscriptionRegistry;
import io.crossasset.ems.api.rest.RestEdgeBinding;
import io.crossasset.ems.fix.FixGateway;
import io.crossasset.ems.fix.FixMessage;
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
import io.crossasset.ems.oms.StagedOrder;
import io.crossasset.ems.transport.session.SequenceRecoveryService;
import io.crossasset.ems.validator.LayeredValidatorPipeline;
import io.crossasset.ems.validator.ValidatorPipeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Multi-surface consistent-view parity (task 8.11), the arch-api-first invariant made executable:
 * the same logical operation submitted via FIX (35=D), the native API surface, and the REST edge
 * binding produces a <b>byte-identical canonical projection</b> on three identically-configured
 * stacks, and the same rejection produces the <b>same catalog code</b> on every surface. Source of
 * entry is metadata, not a state fork.
 *
 * <p>The FIX-echo mirror of API-initiated changes (the full mixed-client rule) lands when the
 * client FIX gateway is rewired through the ApiSurface alongside the 15.2 wire smoke.
 */
class SurfaceParityTest {

  private static final String FIGI = "BBG000BLNNH6";
  private static final String BAD_FIGI = "BBG000UNKNOWN";
  private static final char SOH = '\u0001';

  /** One identically-configured stack: AAA + secmaster + OMS + all three surfaces. */
  private static final class Stack {
    final InMemoryStagedOrderManager som;
    final ApiSurface api;
    final RestEdgeBinding rest;
    final FixGateway fix;
    final List<String> fixOutbound = new ArrayList<>();
    final long sessionId;

    Stack() {
      InMemoryAaaService aaa =
          new InMemoryAaaService(
              new InMemoryAaaEventLog(), null, new SequenceRecoveryService(() -> 0L));
      InMemorySecurityMasterService secMaster = new InMemorySecurityMasterService();
      ValidatorPipeline pipeline = new LayeredValidatorPipeline(aaa, secMaster, null);
      som = new InMemoryStagedOrderManager(pipeline);
      SubscriptionRegistry subscriptions = new SubscriptionRegistry();
      api =
          new ApiSurface(
              aaa, som, new InMemoryRouteManager(som), subscriptions, (sid, subId, event) -> {});
      rest = new RestEdgeBinding(aaa, api, subscriptions);
      fix =
          new FixGateway(
              som,
              new SequenceRecoveryService(() -> 0L),
              (sid, seq, raw) -> fixOutbound.add(raw),
              "EMS",
              "CLIENT");

      aaa.registerCredential("tok-p", "firm-a", "desk-1", "trader-1", Set.of());
      LogonOutcome outcome = aaa.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "tok-p"));
      sessionId = ((LogonOutcome.Accepted) outcome).session().sessionId();

      InstrumentCore core =
          new InstrumentCore(
              FIGI,
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
    }
  }

  // ── Parity: accepted order ───────────────────────────────────────────────────

  @Test
  void sameOrder_viaFixApiRest_byteIdenticalCanonicalProjection() {
    // FIX surface.
    Stack fixStack = new Stack();
    fixStack.fix.onLogon(fixStack.sessionId, 1L);
    fixStack.fix.onInbound(fixStack.sessionId, newOrderSingle(fixStack.sessionId, "CL-P1", FIGI));
    StagedOrder viaFix = onlyOrder(fixStack);

    // Native API surface.
    Stack apiStack = new Stack();
    ApiResponse apiResponse =
        apiStack.api.execute(
            new ApiRequest(
                "req-p1",
                apiStack.sessionId,
                1,
                ApiOperation.STAGE_ORDERS,
                List.of(new ApiItem.StageOrder("CL-P1", FIGI, 1, 100, 1_012_500L, "ACC1", 0))));
    assertThat(apiResponse.summary().ok()).isEqualTo(1);
    StagedOrder viaApi = onlyOrder(apiStack);

    // REST edge binding.
    Stack restStack = new Stack();
    RestEdgeBinding.HttpResult restResult =
        restStack.rest.handle(
            "POST",
            "/api/v1/stage_orders",
            Map.of(),
            Map.of("x-ems-session", Long.toString(restStack.sessionId)),
            """
            {"requestId":"req-p1","sessionSeq":1,"items":[
              {"clOrdId":"CL-P1","figi":"%s","side":1,"qty":100,"price":1012500,"account":"ACC1","tif":0}
            ]}
            """
                .formatted(FIGI));
    assertThat(restResult.status()).isEqualTo(200);
    StagedOrder viaRest = onlyOrder(restStack);

    String canonicalFix = canonical(viaFix);
    assertThat(canonical(viaApi)).isEqualTo(canonicalFix);
    assertThat(canonical(viaRest)).isEqualTo(canonicalFix);
  }

  // ── Parity: rejection codes ──────────────────────────────────────────────────

  @Test
  void sameReject_viaFixApiRest_sameCatalogCode() {
    Stack fixStack = new Stack();
    fixStack.fix.onLogon(fixStack.sessionId, 1L);
    fixStack.fix.onInbound(
        fixStack.sessionId, newOrderSingle(fixStack.sessionId, "CL-P2", BAD_FIGI));
    String fixRejectText =
        fixStack.fixOutbound.stream()
            .map(FixMessage::parse)
            .filter(m -> "j".equals(m.get(35)))
            .findFirst()
            .orElseThrow()
            .get(58);

    Stack apiStack = new Stack();
    ApiResponse apiResponse =
        apiStack.api.execute(
            new ApiRequest(
                "req-p2",
                apiStack.sessionId,
                1,
                ApiOperation.STAGE_ORDERS,
                List.of(new ApiItem.StageOrder("CL-P2", BAD_FIGI, 1, 100, null, "ACC1", 0))));
    String apiCode = apiResponse.results().get(0).errorCode();

    Stack restStack = new Stack();
    RestEdgeBinding.HttpResult restResult =
        restStack.rest.handle(
            "POST",
            "/api/v1/stage_orders",
            Map.of(),
            Map.of("x-ems-session", Long.toString(restStack.sessionId)),
            """
            {"requestId":"req-p2","sessionSeq":1,"items":[
              {"clOrdId":"CL-P2","figi":"%s","side":1,"qty":100,"account":"ACC1"}
            ]}
            """
                .formatted(BAD_FIGI));

    assertThat(apiCode).startsWith("EMS-");
    assertThat(fixRejectText).contains(apiCode);
    assertThat(restResult.body()).contains(apiCode);
  }

  // ── helpers ─────────────────────────────────────────────────────────────────

  /** Canonical, deterministic rendering of the full order projection for byte comparison. */
  private static String canonical(StagedOrder order) {
    var ctx = order.fsmContext();
    return String.join(
        "|",
        order.orderId(),
        order.clOrdId(),
        ctx.instrumentId(),
        Integer.toString(ctx.side()),
        Long.toString(ctx.orderQty()),
        String.valueOf(ctx.price()),
        Long.toString(ctx.cumQty()),
        Long.toString(ctx.leavesQty()),
        ctx.account(),
        Integer.toString(ctx.tif()),
        ctx.initialClOrdId(),
        Long.toString(ctx.orderVersion()),
        order.fsmState().name(),
        order.subState().name());
  }

  private static StagedOrder onlyOrder(Stack stack) {
    return stack.som.findOrder("EMS-ORD-1").orElseThrow();
  }

  private static String newOrderSingle(long sessionId, String clOrdId, String figi) {
    return "8=FIX.4.4"
        + SOH
        + "35=D"
        + SOH
        + "34=1"
        + SOH
        + "49=CLIENT"
        + SOH
        + "56=EMS"
        + SOH
        + "11="
        + clOrdId
        + SOH
        + "48="
        + figi
        + SOH
        + "54=1"
        + SOH
        + "38=100"
        + SOH
        + "44=1012500"
        + SOH
        + "1=ACC1"
        + SOH
        + "10=000"
        + SOH;
  }
}
