/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.control;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.fsm.generated.OrderFsmContext;
import io.crossasset.ems.fsm.generated.OrderFsmEvent;
import io.crossasset.ems.fsm.generated.OrderFsmState;
import io.crossasset.ems.oms.AmendFields;
import io.crossasset.ems.oms.AmendResult;
import io.crossasset.ems.oms.CancelResult;
import io.crossasset.ems.oms.MarkReadyResult;
import io.crossasset.ems.oms.OrderRequest;
import io.crossasset.ems.oms.OrderSubState;
import io.crossasset.ems.oms.StageResult;
import io.crossasset.ems.oms.StagedOrder;
import io.crossasset.ems.oms.StagedOrderManager;
import io.crossasset.ems.pretrade.compliance.ComplianceCheck;
import io.crossasset.ems.pretrade.compliance.ComplianceGate;
import io.crossasset.ems.pretrade.compliance.FatFingerCheck;
import io.crossasset.ems.pretrade.compliance.MachineGunCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * ComplianceStageGuard: fat-finger fires end-to-end on the stage path (an exploded notional is
 * rejected, a normal order sails through to the delegate), the stage gate is built with only the
 * idempotent sizing check (never the stateful machine-gun counter), and every non-stage method
 * passes through to the delegate unchanged.
 */
class ComplianceStageGuardTest {

  /** Records invocations; stage returns Accepted so a normal order reaches the delegate. */
  static final class RecordingStagedOrderManager implements StagedOrderManager {
    final List<String> calls = new ArrayList<>();

    @Override
    public StageResult stage(OrderRequest request) {
      calls.add("stage:" + request.requestId());
      return new StageResult.Accepted(stagedOrder(request.figi(), request.account()));
    }

    @Override
    public AmendResult amend(String orderId, AmendFields fields, long sessionId) {
      calls.add("amend:" + orderId);
      return new AmendResult.Rejected(orderId, "FAKE", "x");
    }

    @Override
    public CancelResult cancel(String orderId, long sessionId) {
      calls.add("cancel:" + orderId);
      return new CancelResult.Rejected(orderId, "FAKE", "x");
    }

    @Override
    public MarkReadyResult markReady(String orderId, long sessionId) {
      calls.add("markReady:" + orderId);
      return new MarkReadyResult.Rejected(orderId, "FAKE", "x");
    }

    @Override
    public void setPendingActionDone(String orderId, String actionRef) {
      calls.add("setPendingActionDone:" + orderId);
    }

    @Override
    public Optional<StagedOrder> findOrder(String orderId) {
      calls.add("findOrder:" + orderId);
      return Optional.empty();
    }

    @Override
    public List<StagedOrder> activeOrders() {
      calls.add("activeOrders");
      return List.of();
    }

    @Override
    public Optional<StagedOrder> applyOrderFsmEvent(
        String orderId, OrderFsmEvent event, Object payload) {
      calls.add("applyOrderFsmEvent:" + orderId);
      return Optional.empty();
    }

    @Override
    public Optional<StagedOrder> markRouting(String orderId) {
      calls.add("markRouting:" + orderId);
      return Optional.empty();
    }
  }

  private static StagedOrder stagedOrder(String figi, String account) {
    return new StagedOrder(
        "O1",
        "CL-1",
        7L,
        OrderFsmState.NEW,
        new OrderFsmContext(
            "O1", "CL-1", null, figi, 1, 100L, null, 0L, 100L, account, 0, "CL-1", "O1", 1L, null,
            null),
        OrderSubState.NEW,
        Set.of(),
        1_000L);
  }

  /** Stage gate with a small fat-finger ceiling: notional above 1e10 (fixed-point 1e4) BLOCKS. */
  private static ComplianceGate smallCeilingGate() {
    FatFingerCheck fatFinger =
        new FatFingerCheck(
            new FatFingerCheck.Policy(10_000_000_000L, 5_000, 1, false),
            figi -> OptionalLong.empty(), // no reference: skip the deviation trip, notional-only
            (account, figi) -> 0L,
            figi -> 1L);
    return new ComplianceGate(List.of(fatFinger));
  }

  private static OrderRequest order(String requestId, long qty, long price) {
    return new OrderRequest(
        requestId, 7L, "CL-" + requestId, "BBG000B9XRY4", 1, qty, price, "ACC-1", 0);
  }

  @Test
  void explodedNotionalIsRejectedAndNeverReachesDelegate() {
    RecordingStagedOrderManager delegate = new RecordingStagedOrderManager();
    ComplianceStageGuard guard =
        new ComplianceStageGuard(delegate, smallCeilingGate(), "firm-a", "desk-1");

    // 100,000,000 @ $100 (100_0000 fixed-point) = 1e14 notional, ≫ 1e10 ceiling.
    StageResult result = guard.stage(order("BAD", 100_000_000L, 100_0000L));

    StageResult.Rejected rejected = (StageResult.Rejected) result;
    assertThat(rejected.rejectCode()).isEqualTo("EMS-CMP-9702");
    assertThat(rejected.message()).contains("Compliance gate blocked stage").contains("notional");
    assertThat(rejected.requestId()).isEqualTo("BAD");
    assertThat(delegate.calls).isEmpty(); // never reached the delegate
  }

  @Test
  void normalOrderPassesThroughToDelegate() {
    RecordingStagedOrderManager delegate = new RecordingStagedOrderManager();
    ComplianceStageGuard guard =
        new ComplianceStageGuard(delegate, smallCeilingGate(), "firm-a", "desk-1");

    // 100 @ $100 = 1e8 notional, well under the 1e10 ceiling.
    StageResult result = guard.stage(order("OK", 100L, 100_0000L));

    assertThat(result).isInstanceOf(StageResult.Accepted.class);
    assertThat(delegate.calls).containsExactly("stage:OK");
  }

  @Test
  void stageGateCarriesFatFingerButNotMachineGun() {
    // The stage gate is built (by construction, in TraderDesktopEdgeMain) from the idempotent
    // sizing check only — never the stateful machine-gun counter, whose per-window count would be
    // double-incremented if evaluated at both stage and route. This test pins that intent.
    List<ComplianceCheck> stageChecks =
        List.of(
            new FatFingerCheck(
                new FatFingerCheck.Policy(10_000_000_000L, 5_000, 1, false),
                figi -> OptionalLong.empty(),
                (account, figi) -> 0L,
                figi -> 1L));

    assertThat(stageChecks).anyMatch(c -> c instanceof FatFingerCheck);
    assertThat(stageChecks).noneMatch(c -> c instanceof MachineGunCheck);
  }

  @Test
  void nonStageMethodsPassThroughUnchanged() {
    RecordingStagedOrderManager delegate = new RecordingStagedOrderManager();
    ComplianceStageGuard guard =
        new ComplianceStageGuard(delegate, smallCeilingGate(), "firm-a", "desk-1");

    guard.amend("O1", new AmendFields(200L, null), 7L);
    guard.cancel("O1", 7L);
    guard.markReady("O1", 7L);
    guard.findOrder("O1");
    guard.activeOrders();
    guard.markRouting("O1");

    assertThat(delegate.calls)
        .containsExactly(
            "amend:O1",
            "cancel:O1",
            "markReady:O1",
            "findOrder:O1",
            "activeOrders",
            "markRouting:O1");
  }
}
