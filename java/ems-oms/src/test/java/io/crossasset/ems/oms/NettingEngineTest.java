/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import static org.junit.jupiter.api.Assertions.*;

import io.crossasset.ems.aaa.CredentialKind;
import io.crossasset.ems.aaa.InMemoryAaaEventLog;
import io.crossasset.ems.aaa.InMemoryAaaService;
import io.crossasset.ems.aaa.LogonCredentials;
import io.crossasset.ems.aaa.LogonOutcome;
import io.crossasset.ems.fsm.generated.OrderFsmState;
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
import io.crossasset.ems.validator.LayeredValidatorPipeline;
import io.crossasset.ems.validator.ValidatorPipeline;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link InMemoryNettingEngine}. Covers netting-key bucketing (PB / PAC / value-date
 * isolation), residual-parent collapse, net-to-zero policy, progressive fill + internal-cross
 * booking, do-not-net opt-out, and un-net guards. Per arch-fx-netting.md, task 7.6.
 */
class NettingEngineTest {

  private static final String TOKEN = "tok-trader-1";
  private static final String FIGI_EURUSD = "BBG000BLNNH6";
  private static final int BUY = 1;
  private static final int SELL = 2;
  private static final int TIF_DAY = 0;
  private static final String T2 = "2026-06-12";
  private static final String T30 = "2026-07-10";
  private static final String PB_GS = "PB-GS";
  private static final String PB_JPM = "PB-JPM";

  private InMemoryStagedOrderManager som;
  private InMemoryNettingEngine netting;
  private long sessionId;

  @BeforeEach
  void setUp() {
    InMemoryAaaService aaaService = new InMemoryAaaService(new InMemoryAaaEventLog());
    InMemorySecurityMasterService secMaster = new InMemorySecurityMasterService();
    ValidatorPipeline pipeline = new LayeredValidatorPipeline(aaaService, secMaster, null);
    som = new InMemoryStagedOrderManager(pipeline);
    netting = new InMemoryNettingEngine(som, pipeline);

    aaaService.registerCredential(TOKEN, "firm-a", "desk-fx", "trader-1", Set.of());
    LogonOutcome outcome = aaaService.logon(LogonCredentials.fresh(CredentialKind.TOKEN, TOKEN));
    sessionId = ((LogonOutcome.Accepted) outcome).session().sessionId();

    SecurityMasterSnapshot snap =
        SecurityMasterSnapshot.EMPTY.apply(
            new SecurityMasterEvent.InstrumentCreated(
                new InstrumentVersioned(fxInstrument(FIGI_EURUSD), null), 1L));
    secMaster.publish(snap);
  }

  // --- net(): bucketing + collapse ---

  @Test
  void net_opposingOrders_collapseToResidualParent() {
    String buy = readyFxOrder(BUY, 80_000_000);
    String sell = readyFxOrder(SELL, 30_000_000);
    NetResult result =
        netting.net(request("req-1", candidate(buy, PB_GS, T2), candidate(sell, PB_GS, T2)));
    NetResult.Netted ok = assertInstanceOf(NetResult.Netted.class, result);
    assertEquals(1, ok.groups().size());
    NetGroup group = ok.groups().get(0);
    assertEquals(50_000_000, group.residualQty());
    assertEquals(BUY, group.residualSide());
    assertEquals(30_000_000, group.matchedQty());
    assertNotNull(group.parentOrderId());
    StagedOrder parent = som.findOrder(group.parentOrderId()).orElseThrow();
    assertEquals(50_000_000, parent.fsmContext().orderQty());
    assertEquals(BUY, parent.fsmContext().side());
    assertTrue(parent.isReady(), "netted parent must be routable");
  }

  @Test
  void net_primeBrokerIsolation_separateBuckets() {
    String buyGs = readyFxOrder(BUY, 50_000_000);
    String sellGs = readyFxOrder(SELL, 30_000_000);
    String buyJpm = readyFxOrder(BUY, 20_000_000);
    NetResult.Netted ok =
        assertInstanceOf(
            NetResult.Netted.class,
            netting.net(
                request(
                    "req-1",
                    candidate(buyGs, PB_GS, T2),
                    candidate(sellGs, PB_GS, T2),
                    candidate(buyJpm, PB_JPM, T2))));
    assertEquals(1, ok.groups().size(), "JPM single-side bucket must not net");
    assertEquals(List.of(buyJpm), ok.passthrough());
  }

  @Test
  void net_differentValueDates_doNotNet() {
    String buySpot = readyFxOrder(BUY, 10_000_000);
    String sellFwd = readyFxOrder(SELL, 10_000_000);
    NetResult.Netted ok =
        assertInstanceOf(
            NetResult.Netted.class,
            netting.net(
                request("req-1", candidate(buySpot, PB_GS, T2), candidate(sellFwd, PB_GS, T30))));
    assertTrue(ok.groups().isEmpty());
    assertEquals(Set.of(buySpot, sellFwd), Set.copyOf(ok.passthrough()));
  }

  @Test
  void net_pacIsolation_differentPacsDoNotNet() {
    String buy = readyFxOrder(BUY, 10_000_000);
    String sell = readyFxOrder(SELL, 10_000_000);
    NetResult.Netted ok =
        assertInstanceOf(
            NetResult.Netted.class,
            netting.net(
                request(
                    "req-1",
                    candidate(buy, PB_GS, T2, "CPTY-A", false),
                    candidate(sell, PB_GS, T2, "CPTY-B", false))));
    assertTrue(ok.groups().isEmpty());
  }

  @Test
  void net_doNotNetFlag_passesThrough() {
    String buy = readyFxOrder(BUY, 10_000_000);
    String sell = readyFxOrder(SELL, 10_000_000);
    NetResult.Netted ok =
        assertInstanceOf(
            NetResult.Netted.class,
            netting.net(
                request(
                    "req-1",
                    candidate(buy, PB_GS, T2, null, true),
                    candidate(sell, PB_GS, T2, null, false))));
    assertTrue(ok.groups().isEmpty());
    assertTrue(ok.passthrough().contains(buy));
  }

  @Test
  void net_toZero_allowed_formsParentlessGroup() {
    String buy = readyFxOrder(BUY, 10_000_000);
    String sell = readyFxOrder(SELL, 10_000_000);
    NetResult.Netted ok =
        assertInstanceOf(
            NetResult.Netted.class,
            netting.net(request("req-1", candidate(buy, PB_GS, T2), candidate(sell, PB_GS, T2))));
    NetGroup group = ok.groups().get(0);
    assertNull(group.parentOrderId());
    assertEquals(0, group.residualQty());
    assertEquals(10_000_000, group.matchedQty());
  }

  @Test
  void net_toZero_blocked_rejects2203AndPassesThrough() {
    String buy = readyFxOrder(BUY, 10_000_000);
    String sell = readyFxOrder(SELL, 10_000_000);
    NettingRequest req =
        new NettingRequest(
            "req-1",
            sessionId,
            List.of(candidate(buy, PB_GS, T2), candidate(sell, PB_GS, T2)),
            "acc-net",
            false);
    NetResult result = netting.net(req);
    NetResult.Rejected rej = assertInstanceOf(NetResult.Rejected.class, result);
    assertEquals("EMS-ORD-2203", rej.rejectCode());
  }

  @Test
  void net_unknownOrder_rejected4001() {
    NetResult result = netting.net(request("req-1", candidate("NOPE", PB_GS, T2)));
    NetResult.Rejected rej = assertInstanceOf(NetResult.Rejected.class, result);
    assertEquals("EMS-ORD-4001", rej.rejectCode());
  }

  @Test
  void net_childAlreadyInActiveGroup_rejected3003() {
    String buy = readyFxOrder(BUY, 20_000_000);
    String sell = readyFxOrder(SELL, 10_000_000);
    netting.net(request("req-1", candidate(buy, PB_GS, T2), candidate(sell, PB_GS, T2)));
    String sell2 = readyFxOrder(SELL, 5_000_000);
    NetResult result =
        netting.net(request("req-2", candidate(buy, PB_GS, T2), candidate(sell2, PB_GS, T2)));
    NetResult.Rejected rej = assertInstanceOf(NetResult.Rejected.class, result);
    assertEquals("EMS-ORD-3003", rej.rejectCode());
  }

  // --- allocateNetFill: progressive booking ---

  @Test
  void allocateNetFill_booksResidualSideProRataAndCrossesMatched() {
    String buy1 = readyFxOrder(BUY, 60_000_000);
    String buy2 = readyFxOrder(BUY, 20_000_000);
    String sell = readyFxOrder(SELL, 30_000_000);
    NetGroup group =
        netGroup(
            request(
                "req-1",
                candidate(buy1, PB_GS, T2),
                candidate(buy2, PB_GS, T2),
                candidate(sell, PB_GS, T2)));
    assertEquals(50_000_000, group.residualQty());

    // Half the residual prints: buys book half their total demand, sells cross half the match.
    NettingEventResult result = netting.allocateNetFill(group.groupId(), 25_000_000, 108_500L);
    NettingEventResult.Applied ok = assertInstanceOf(NettingEventResult.Applied.class, result);
    assertEquals(30_000_000, cumQty(buy1));
    assertEquals(10_000_000, cumQty(buy2));
    assertEquals(15_000_000, cumQty(sell));
    assertTrue(ok.allocations().stream().allMatch(a -> a.px() == 108_500L));
  }

  @Test
  void allocateNetFill_finalFill_completesAllChildrenExactly() {
    String buy1 = readyFxOrder(BUY, 60_000_000);
    String buy2 = readyFxOrder(BUY, 20_000_000);
    String sell = readyFxOrder(SELL, 30_000_000);
    NetGroup group =
        netGroup(
            request(
                "req-1",
                candidate(buy1, PB_GS, T2),
                candidate(buy2, PB_GS, T2),
                candidate(sell, PB_GS, T2)));
    netting.allocateNetFill(group.groupId(), 25_000_000, 108_500L);
    netting.allocateNetFill(group.groupId(), 25_000_000, 108_700L);
    assertEquals(60_000_000, cumQty(buy1));
    assertEquals(20_000_000, cumQty(buy2));
    assertEquals(30_000_000, cumQty(sell));
    for (String id : List.of(buy1, buy2, sell)) {
      assertEquals(OrderFsmState.FILLED, som.findOrder(id).orElseThrow().fsmState());
    }
  }

  @Test
  void allocateNetFill_exceedsResidual_rejected3003() {
    String buy = readyFxOrder(BUY, 20_000_000);
    String sell = readyFxOrder(SELL, 10_000_000);
    NetGroup group =
        netGroup(request("req-1", candidate(buy, PB_GS, T2), candidate(sell, PB_GS, T2)));
    NettingEventResult result = netting.allocateNetFill(group.groupId(), 10_000_001, 100L);
    NettingEventResult.Rejected rej = assertInstanceOf(NettingEventResult.Rejected.class, result);
    assertEquals("EMS-ORD-3003", rej.rejectCode());
  }

  @Test
  void bookInternalCross_zeroGroup_fillsAllChildrenAtCrossRate() {
    String buy = readyFxOrder(BUY, 10_000_000);
    String sell = readyFxOrder(SELL, 10_000_000);
    NetGroup group =
        netGroup(request("req-1", candidate(buy, PB_GS, T2), candidate(sell, PB_GS, T2)));
    assertNull(group.parentOrderId());
    NettingEventResult result = netting.bookInternalCross(group.groupId(), 108_650L);
    NettingEventResult.Applied ok = assertInstanceOf(NettingEventResult.Applied.class, result);
    assertEquals(2, ok.allocations().size());
    assertEquals(OrderFsmState.FILLED, som.findOrder(buy).orElseThrow().fsmState());
    assertEquals(OrderFsmState.FILLED, som.findOrder(sell).orElseThrow().fsmState());
  }

  @Test
  void bookInternalCross_onResidualGroup_rejected3003() {
    String buy = readyFxOrder(BUY, 20_000_000);
    String sell = readyFxOrder(SELL, 10_000_000);
    NetGroup group =
        netGroup(request("req-1", candidate(buy, PB_GS, T2), candidate(sell, PB_GS, T2)));
    NettingEventResult result = netting.bookInternalCross(group.groupId(), 100L);
    assertInstanceOf(NettingEventResult.Rejected.class, result);
  }

  // --- unnet ---

  @Test
  void unnet_beforeFills_cancelsParentAndFreesChildren() {
    String buy = readyFxOrder(BUY, 20_000_000);
    String sell = readyFxOrder(SELL, 10_000_000);
    NetGroup group =
        netGroup(request("req-1", candidate(buy, PB_GS, T2), candidate(sell, PB_GS, T2)));
    NettingEventResult result = netting.unnet(group.groupId(), sessionId);
    assertInstanceOf(NettingEventResult.Applied.class, result);
    assertTrue(netting.findGroup(group.groupId()).isEmpty());
    // children re-nettable
    NetResult again =
        netting.net(request("req-2", candidate(buy, PB_GS, T2), candidate(sell, PB_GS, T2)));
    assertInstanceOf(NetResult.Netted.class, again);
  }

  @Test
  void unnet_afterFills_rejected2210() {
    String buy = readyFxOrder(BUY, 20_000_000);
    String sell = readyFxOrder(SELL, 10_000_000);
    NetGroup group =
        netGroup(request("req-1", candidate(buy, PB_GS, T2), candidate(sell, PB_GS, T2)));
    netting.allocateNetFill(group.groupId(), 5_000_000, 100L);
    NettingEventResult result = netting.unnet(group.groupId(), sessionId);
    NettingEventResult.Rejected rej = assertInstanceOf(NettingEventResult.Rejected.class, result);
    assertEquals("EMS-ORD-2210", rej.rejectCode());
  }

  @Test
  void findGroup_unknown_returnsEmpty() {
    assertTrue(netting.findGroup("NOPE").isEmpty());
  }

  // --- helpers ---

  private long cumQty(String orderId) {
    return som.findOrder(orderId).orElseThrow().fsmContext().cumQty();
  }

  private NetGroup netGroup(NettingRequest request) {
    NetResult.Netted ok = assertInstanceOf(NetResult.Netted.class, netting.net(request));
    assertEquals(1, ok.groups().size());
    return ok.groups().get(0);
  }

  private NettingRequest request(String requestId, NettingCandidate... candidates) {
    return new NettingRequest(requestId, sessionId, List.of(candidates), "acc-net", true);
  }

  private NettingCandidate candidate(String orderId, String accountGroup, String valueDate) {
    return candidate(orderId, accountGroup, valueDate, null, false);
  }

  private NettingCandidate candidate(
      String orderId,
      String accountGroup,
      String valueDate,
      @Nullable String pac,
      boolean doNotNet) {
    return new NettingCandidate(orderId, "EURUSD", valueDate, accountGroup, pac, doNotNet);
  }

  private String readyFxOrder(int side, long qty) {
    String requestId = "req-fx-" + System.nanoTime() + "-" + qty + "-" + side;
    StageResult sr =
        som.stage(
            new OrderRequest(
                requestId,
                sessionId,
                "CL-" + requestId,
                FIGI_EURUSD,
                side,
                qty,
                null,
                "acc-1",
                TIF_DAY));
    String orderId = ((StageResult.Accepted) sr).order().orderId();
    som.markReady(orderId, sessionId);
    return orderId;
  }

  private static InstrumentCore fxInstrument(String figi) {
    return new InstrumentCore(
        figi,
        "IID-" + figi,
        null,
        null,
        AssetClass.FX,
        InstrumentType.FX_SPOT,
        "EURUSD Spot",
        "FX",
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
  }
}
