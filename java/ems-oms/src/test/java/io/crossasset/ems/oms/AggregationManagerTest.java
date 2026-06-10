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
 * Tests for {@link InMemoryAggregationManager}. Covers eligibility predicates (EMS-ORD-5001,
 * EMS-ORD-5002, EMS-PRM-1601), block-parent creation, pro-rata / sequenced / avg-price fill
 * allocation with rounding policies, child FSM propagation, and unaggregation. Per
 * arch-aggregation.md, task 7.5.
 */
class AggregationManagerTest {

  private static final String TOKEN = "tok-trader-1";
  private static final String FIGI = "BBG000BLNNH6";
  private static final String FIGI_B = "BBG000B9XRY4";
  private static final int BUY = 1;
  private static final int SELL = 2;
  private static final int TIF_DAY = 0;
  private static final int TIF_GTC = 1;

  private InMemoryStagedOrderManager som;
  private InMemoryAggregationManager agg;
  private long sessionId;

  @BeforeEach
  void setUp() {
    InMemoryAaaService aaaService = new InMemoryAaaService(new InMemoryAaaEventLog());
    InMemorySecurityMasterService secMaster = new InMemorySecurityMasterService();
    ValidatorPipeline pipeline = new LayeredValidatorPipeline(aaaService, secMaster, null);
    som = new InMemoryStagedOrderManager(pipeline);
    agg = new InMemoryAggregationManager(som, pipeline);

    aaaService.registerCredential(TOKEN, "firm-a", "desk-1", "trader-1", Set.of());
    LogonOutcome outcome = aaaService.logon(LogonCredentials.fresh(CredentialKind.TOKEN, TOKEN));
    sessionId = ((LogonOutcome.Accepted) outcome).session().sessionId();

    SecurityMasterSnapshot snap =
        SecurityMasterSnapshot.EMPTY
            .apply(
                new SecurityMasterEvent.InstrumentCreated(
                    new InstrumentVersioned(activeInstrument(FIGI), null), 1L))
            .apply(
                new SecurityMasterEvent.InstrumentCreated(
                    new InstrumentVersioned(activeInstrument(FIGI_B), null), 2L));
    secMaster.publish(snap);
  }

  // --- aggregate() ---

  @Test
  void aggregate_threeChildren_createsReadyBlockParent() {
    List<String> children = readyChildren(100_000, 60_000, 40_000);
    AggregateResult result = agg.aggregate(proRataRequest("req-1", children));
    AggregateResult.Aggregated ok = assertInstanceOf(AggregateResult.Aggregated.class, result);
    StagedOrder parent = ok.parent();
    assertEquals(200_000, parent.fsmContext().orderQty());
    assertEquals(FIGI, parent.fsmContext().instrumentId());
    assertEquals(BUY, parent.fsmContext().side());
    assertTrue(parent.isReady(), "block parent must be routable");
    assertEquals(children, ok.group().childOrderIds());
  }

  @Test
  void aggregate_parentLimitPrice_isMostConservativeOfChildren() {
    String c1 = readyChild(100, BUY, FIGI, 215_200L, TIF_DAY);
    String c2 = readyChild(100, BUY, FIGI, 215_100L, TIF_DAY);
    AggregateResult.Aggregated ok =
        assertInstanceOf(
            AggregateResult.Aggregated.class,
            agg.aggregate(proRataRequest("req-1", List.of(c1, c2))));
    assertEquals(215_100L, ok.parent().fsmContext().price(), "BUY block takes the lowest limit");
  }

  @Test
  void aggregate_differentInstruments_rejected5001() {
    String c1 = readyChild(100, BUY, FIGI, null, TIF_DAY);
    String c2 = readyChild(100, BUY, FIGI_B, null, TIF_DAY);
    assertEligibilityReject(List.of(c1, c2), "instrument");
  }

  @Test
  void aggregate_differentSides_rejected5001() {
    String c1 = readyChild(100, BUY, FIGI, null, TIF_DAY);
    String c2 = readyChild(100, SELL, FIGI, null, TIF_DAY);
    assertEligibilityReject(List.of(c1, c2), "side");
  }

  @Test
  void aggregate_differentTif_rejected5001() {
    String c1 = readyChild(100, BUY, FIGI, null, TIF_DAY);
    String c2 = readyChild(100, BUY, FIGI, null, TIF_GTC);
    assertEligibilityReject(List.of(c1, c2), "tif");
  }

  @Test
  void aggregate_childNotReady_rejected5001() {
    String c1 = readyChild(100, BUY, FIGI, null, TIF_DAY);
    // staged but never marked ready
    StagedOrder notReady =
        ((StageResult.Accepted)
                som.stage(
                    new OrderRequest(
                        "req-nr", sessionId, "CL-nr", FIGI, BUY, 100, null, "acc-1", TIF_DAY)))
            .order();
    assertEligibilityReject(List.of(c1, notReady.orderId()), "READY");
  }

  @Test
  void aggregate_childAlreadyAggregated_rejected5001() {
    List<String> children = readyChildren(100, 100);
    agg.aggregate(proRataRequest("req-1", children));
    String c3 = readyChild(100, BUY, FIGI, null, TIF_DAY);
    AggregateResult result = agg.aggregate(proRataRequest("req-2", List.of(children.get(0), c3)));
    AggregateResult.Rejected rej = assertInstanceOf(AggregateResult.Rejected.class, result);
    assertEquals("EMS-ORD-5001", rej.rejectCode());
    assertTrue(rej.message().contains("aggregat"), rej.message());
  }

  @Test
  void aggregate_singleChild_rejected5001() {
    String c1 = readyChild(100, BUY, FIGI, null, TIF_DAY);
    assertEligibilityReject(List.of(c1), "2");
  }

  @Test
  void aggregate_missingRounding_rejected5002() {
    List<String> children = readyChildren(100, 100);
    AggregateResult result =
        agg.aggregate(
            new AggregationRequest(
                "req-1",
                sessionId,
                "CL-AGG-1",
                children,
                AllocationRule.PRO_RATA,
                null,
                "acc-agg"));
    AggregateResult.Rejected rej = assertInstanceOf(AggregateResult.Rejected.class, result);
    assertEquals("EMS-ORD-5002", rej.rejectCode());
  }

  @Test
  void aggregate_unknownChild_rejected4001() {
    String c1 = readyChild(100, BUY, FIGI, null, TIF_DAY);
    AggregateResult result = agg.aggregate(proRataRequest("req-1", List.of(c1, "NOPE")));
    AggregateResult.Rejected rej = assertInstanceOf(AggregateResult.Rejected.class, result);
    assertEquals("EMS-ORD-4001", rej.rejectCode());
  }

  // --- allocateFill: PRO_RATA ---

  @Test
  void allocateFill_proRata_exactSplit() {
    List<String> children = readyChildren(100_000, 60_000, 40_000);
    String aggId = aggregate(proRataRequest("req-1", children));
    AggregationEventResult result = agg.allocateFill(aggId, 80_000, 215_100L);
    AggregationEventResult.Applied ok =
        assertInstanceOf(AggregationEventResult.Applied.class, result);
    assertEquals(
        List.of(40_000L, 24_000L, 16_000L),
        ok.allocations().stream().map(ChildAllocation::qty).toList());
    assertTrue(ok.allocations().stream().allMatch(a -> a.px() == 215_100L));
  }

  @Test
  void allocateFill_proRata_roundDown_residualToLastChild() {
    List<String> children = readyChildren(100, 60, 40);
    String aggId = aggregate(proRataRequest("req-1", children));
    // 81 * 0.5 = 40.5 -> 40; 81 * 0.3 = 24.3 -> 24; 81 * 0.2 = 16.2 -> 16; residual 1 -> last
    AggregationEventResult.Applied ok =
        assertInstanceOf(AggregationEventResult.Applied.class, agg.allocateFill(aggId, 81, 100L));
    assertEquals(
        List.of(40L, 24L, 17L), ok.allocations().stream().map(ChildAllocation::qty).toList());
  }

  @Test
  void allocateFill_proRata_distributeResidual_byLargestRemainder() {
    List<String> children = readyChildren(100, 60, 40);
    String aggId =
        aggregate(
            new AggregationRequest(
                "req-1",
                sessionId,
                "CL-AGG-1",
                children,
                AllocationRule.PRO_RATA,
                RoundingPolicy.DISTRIBUTE_RESIDUAL,
                "acc-agg"));
    // raw: 40.5 / 24.3 / 16.2 — residual 1 goes to the largest fractional remainder (.5)
    AggregationEventResult.Applied ok =
        assertInstanceOf(AggregationEventResult.Applied.class, agg.allocateFill(aggId, 81, 100L));
    assertEquals(
        List.of(41L, 24L, 16L), ok.allocations().stream().map(ChildAllocation::qty).toList());
  }

  @Test
  void allocateFill_cumulative_neverExceedsChildQty_andFillsChildren() {
    List<String> children = readyChildren(100, 60, 40);
    String aggId = aggregate(proRataRequest("req-1", children));
    agg.allocateFill(aggId, 120, 100L);
    agg.allocateFill(aggId, 80, 102L);
    AggregationGroup group = agg.findGroup(aggId).orElseThrow();
    assertEquals(200, group.totalAllocated());
    assertEquals(100, group.allocatedQty(children.get(0)));
    assertEquals(60, group.allocatedQty(children.get(1)));
    assertEquals(40, group.allocatedQty(children.get(2)));
    for (String childId : children) {
      StagedOrder child = som.findOrder(childId).orElseThrow();
      assertEquals(OrderFsmState.FILLED, child.fsmState(), "child must be fully filled");
    }
  }

  @Test
  void allocateFill_exceedsRemaining_rejected3003() {
    List<String> children = readyChildren(100, 100);
    String aggId = aggregate(proRataRequest("req-1", children));
    AggregationEventResult result = agg.allocateFill(aggId, 201, 100L);
    AggregationEventResult.Rejected rej =
        assertInstanceOf(AggregationEventResult.Rejected.class, result);
    assertEquals("EMS-ORD-3003", rej.rejectCode());
  }

  // --- allocateFill: SEQUENCED ---

  @Test
  void allocateFill_sequenced_fillsChildrenInDeclaredOrder() {
    List<String> children = readyChildren(100, 60, 40);
    String aggId =
        aggregate(
            new AggregationRequest(
                "req-1",
                sessionId,
                "CL-AGG-1",
                children,
                AllocationRule.SEQUENCED,
                null,
                "acc-agg"));
    AggregationEventResult.Applied ok =
        assertInstanceOf(AggregationEventResult.Applied.class, agg.allocateFill(aggId, 130, 100L));
    assertEquals(2, ok.allocations().size());
    assertEquals(100L, ok.allocations().get(0).qty());
    assertEquals(30L, ok.allocations().get(1).qty());
  }

  // --- allocateFill: AVG_PRICE ---

  @Test
  void allocateFill_avgPrice_allChildrenSeeRunningAverage() {
    List<String> children = readyChildren(100, 100);
    String aggId =
        aggregate(
            new AggregationRequest(
                "req-1",
                sessionId,
                "CL-AGG-1",
                children,
                AllocationRule.AVG_PRICE,
                RoundingPolicy.ROUND_DOWN,
                "acc-agg"));
    agg.allocateFill(aggId, 100, 100L);
    AggregationEventResult.Applied second =
        assertInstanceOf(AggregationEventResult.Applied.class, agg.allocateFill(aggId, 100, 110L));
    // running average across 200 @ (100*100 + 100*110) = 105
    assertTrue(second.allocations().stream().allMatch(a -> a.px() == 105L));
    assertEquals(105L, agg.findGroup(aggId).orElseThrow().avgPx());
  }

  // --- unaggregate ---

  @Test
  void unaggregate_beforeFills_cancelsParentAndFreesChildren() {
    List<String> children = readyChildren(100, 100);
    String aggId = aggregate(proRataRequest("req-1", children));
    AggregationEventResult result = agg.unaggregate(aggId, sessionId);
    assertInstanceOf(AggregationEventResult.Applied.class, result);
    assertTrue(agg.findGroup(aggId).isEmpty(), "group must be removed");
    // children must be re-aggregatable
    AggregateResult again = agg.aggregate(proRataRequest("req-2", children));
    assertInstanceOf(AggregateResult.Aggregated.class, again);
  }

  @Test
  void unaggregate_afterFills_rejected3003() {
    List<String> children = readyChildren(100, 100);
    String aggId = aggregate(proRataRequest("req-1", children));
    agg.allocateFill(aggId, 50, 100L);
    AggregationEventResult result = agg.unaggregate(aggId, sessionId);
    AggregationEventResult.Rejected rej =
        assertInstanceOf(AggregationEventResult.Rejected.class, result);
    assertEquals("EMS-ORD-3003", rej.rejectCode());
  }

  @Test
  void findGroup_unknown_returnsEmpty() {
    assertTrue(agg.findGroup("NOPE").isEmpty());
  }

  // --- helpers ---

  private AggregationRequest proRataRequest(String requestId, List<String> children) {
    return new AggregationRequest(
        requestId,
        sessionId,
        "CL-AGG-" + requestId,
        children,
        AllocationRule.PRO_RATA,
        RoundingPolicy.ROUND_DOWN,
        "acc-agg");
  }

  private String aggregate(AggregationRequest request) {
    return ((AggregateResult.Aggregated) agg.aggregate(request)).group().aggOrderId();
  }

  private void assertEligibilityReject(List<String> children, String messageFragment) {
    AggregateResult result = agg.aggregate(proRataRequest("req-x", children));
    AggregateResult.Rejected rej = assertInstanceOf(AggregateResult.Rejected.class, result);
    assertEquals("EMS-ORD-5001", rej.rejectCode());
    assertTrue(
        rej.message().toLowerCase().contains(messageFragment.toLowerCase()),
        "message should name predicate '" + messageFragment + "': " + rej.message());
  }

  private List<String> readyChildren(long... qtys) {
    java.util.ArrayList<String> ids = new java.util.ArrayList<>();
    for (long qty : qtys) {
      ids.add(readyChild(qty, BUY, FIGI, null, TIF_DAY));
    }
    return List.copyOf(ids);
  }

  private String readyChild(long qty, int side, String figi, @Nullable Long price, int tif) {
    String requestId = "req-c-" + System.nanoTime() + "-" + qty;
    StageResult sr =
        som.stage(
            new OrderRequest(
                requestId, sessionId, "CL-" + requestId, figi, side, qty, price, "acc-1", tif));
    String orderId = ((StageResult.Accepted) sr).order().orderId();
    som.markReady(orderId, sessionId);
    return orderId;
  }

  private static InstrumentCore activeInstrument(String figi) {
    return new InstrumentCore(
        figi,
        "IID-" + figi,
        null,
        null,
        AssetClass.EQUITY,
        InstrumentType.COMMON_STOCK,
        "Test Instrument",
        "Test Issuer",
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
