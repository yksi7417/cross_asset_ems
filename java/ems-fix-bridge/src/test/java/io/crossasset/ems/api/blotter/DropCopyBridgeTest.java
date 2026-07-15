/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.blotter;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.aaa.CredentialKind;
import io.crossasset.ems.aaa.InMemoryAaaEventLog;
import io.crossasset.ems.aaa.InMemoryAaaService;
import io.crossasset.ems.aaa.LogonCredentials;
import io.crossasset.ems.aaa.LogonOutcome;
import io.crossasset.ems.api.SubscriptionRegistry;
import io.crossasset.ems.fix.dropcopy.DropCopyService;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Fill stream in, drop-copy ExecutionReport out -- the account/cumQty/leavesQty come from the
 * staged order's FSM context, firm/desk from the session identity, exactly as the compliance route
 * guard resolves the same fields for the list gate.
 */
class DropCopyBridgeTest {

  private static final String ORDER_ID = "O-1";

  private final SubscriptionRegistry subscriptions = new SubscriptionRegistry();
  private final List<String> wire = new java.util.ArrayList<>();
  private InMemoryAaaService aaa;
  private long sessionId;

  /** findOrder returns one canned order; every mutator is unused by the bridge. */
  private static final class OneOrder implements StagedOrderManager {
    private final StagedOrder order;

    OneOrder(long sessionId) {
      order =
          new StagedOrder(
              ORDER_ID,
              "CL-1",
              sessionId,
              OrderFsmState.NEW,
              new OrderFsmContext(
                  ORDER_ID,
                  "CL-1",
                  null,
                  "BBG000BLNNH6",
                  1,
                  100L,
                  100_00L,
                  60L,
                  40L,
                  "ACC-1",
                  0,
                  "CL-1",
                  ORDER_ID,
                  1L,
                  null,
                  null),
              OrderSubState.READY,
              Set.of(),
              1_000L);
    }

    @Override
    public Optional<StagedOrder> findOrder(String orderId) {
      return orderId.equals(ORDER_ID) ? Optional.of(order) : Optional.empty();
    }

    @Override
    public StageResult stage(OrderRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public AmendResult amend(String orderId, AmendFields fields, long sessionId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CancelResult cancel(String orderId, long sessionId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public MarkReadyResult markReady(String orderId, long sessionId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setPendingActionDone(String orderId, String actionRef) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<StagedOrder> activeOrders() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<StagedOrder> applyOrderFsmEvent(
        String orderId, OrderFsmEvent event, Object payload) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<StagedOrder> markRouting(String orderId) {
      throw new UnsupportedOperationException();
    }
  }

  @BeforeEach
  void setUp() throws Exception {
    aaa = new InMemoryAaaService(new InMemoryAaaEventLog());
    aaa.registerCredential("tok-1", "firm-a", "desk-1", "trader-1", Set.of());
    sessionId =
        ((LogonOutcome.Accepted) aaa.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "tok-1")))
            .session()
            .sessionId();
    DropCopyService dropCopy = new DropCopyService("EMS");
    dropCopy.subscribe(
        DropCopyService.ScopeKind.FIRM,
        "firm-a",
        "RISK",
        (subscriptionId, seq, raw) -> wire.add(raw));
    new DropCopyBridge(subscriptions, new OneOrder(sessionId), aaa, dropCopy).attach();
  }

  private void publishFill() {
    var row = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
    row.put("execId", "EX-1");
    row.put("orderId", ORDER_ID);
    row.put("routeId", "RT-1");
    row.put("figi", "BBG000BLNNH6");
    row.put("side", 1);
    row.put("lastQty", 60L);
    row.put("lastPx", 100_00L);
    row.put("ts", 123_456L);
    subscriptions.publish(BlotterPublisher.TOPIC_FILLS, "FillRow", "EX-1", row.toString());
  }

  @Test
  void fillMirroredToFirmScopedSubscription() {
    publishFill();

    assertThat(wire).hasSize(1);
    String raw = wire.get(0);
    assertThat(raw).contains("797=Y"); // CopyMsgIndicator
    assertThat(raw).contains("1=ACC-1"); // Account, from the FSM context
    assertThat(raw).contains("32=60"); // LastQty
    assertThat(raw).contains("14=60"); // CumQty, from the FSM context
    assertThat(raw).contains("151=40"); // LeavesQty, from the FSM context
  }

  @Test
  void unknownOrderIsSkippedNotFatal() {
    var row = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
    row.put("execId", "EX-2");
    row.put("orderId", "O-UNKNOWN");
    row.put("figi", "BBG000BLNNH6");
    row.put("side", 1);
    row.put("lastQty", 10L);
    row.put("lastPx", 100_00L);
    row.put("ts", 1L);
    subscriptions.publish(BlotterPublisher.TOPIC_FILLS, "FillRow", "EX-2", row.toString());

    assertThat(wire).isEmpty();
  }

  @Test
  void malformedFillRowIsIgnoredNotFatal() {
    subscriptions.publish(BlotterPublisher.TOPIC_FILLS, "FillRow", "junk", "{not json");
    assertThat(wire).isEmpty();
  }
}
