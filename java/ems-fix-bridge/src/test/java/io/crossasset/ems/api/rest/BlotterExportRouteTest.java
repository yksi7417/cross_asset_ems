/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.aaa.CredentialKind;
import io.crossasset.ems.aaa.InMemoryAaaEventLog;
import io.crossasset.ems.aaa.InMemoryAaaService;
import io.crossasset.ems.aaa.LogonCredentials;
import io.crossasset.ems.aaa.LogonOutcome;
import io.crossasset.ems.api.ApiSurface;
import io.crossasset.ems.api.SubscriptionRegistry;
import io.crossasset.ems.fsm.generated.OrderFsmContext;
import io.crossasset.ems.fsm.generated.OrderFsmEvent;
import io.crossasset.ems.fsm.generated.OrderFsmState;
import io.crossasset.ems.oms.AmendFields;
import io.crossasset.ems.oms.AmendResult;
import io.crossasset.ems.oms.CancelResult;
import io.crossasset.ems.oms.InMemoryRouteManager;
import io.crossasset.ems.oms.MarkReadyResult;
import io.crossasset.ems.oms.OrderRequest;
import io.crossasset.ems.oms.OrderSubState;
import io.crossasset.ems.oms.StageResult;
import io.crossasset.ems.oms.StagedOrder;
import io.crossasset.ems.oms.StagedOrderManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /api/v1/blotter/export.csv} (task 8.7 wiring): session-authenticated, own-orders-only
 * CSV export -- {@link io.crossasset.ems.bulk.BlotterExporter} itself is already unit-tested; this
 * proves the REST route resolves the right scope and content type.
 */
class BlotterExportRouteTest {

  private RestEdgeBinding binding;
  private long sessionA;
  private long sessionB;

  private static final class TwoOwnersOrders implements StagedOrderManager {
    private final List<StagedOrder> orders;

    TwoOwnersOrders(long sessionA, long sessionB) {
      orders = List.of(order("O-A", sessionA, "ACC-A"), order("O-B", sessionB, "ACC-B"));
    }

    private static StagedOrder order(String orderId, long sessionId, String account) {
      return new StagedOrder(
          orderId,
          "CL-" + orderId,
          sessionId,
          OrderFsmState.NEW,
          new OrderFsmContext(
              orderId,
              "CL-" + orderId,
              null,
              "BBG000BLNNH6",
              1,
              100L,
              100_00L,
              0L,
              100L,
              account,
              0,
              "CL-" + orderId,
              orderId,
              1L,
              null,
              null),
          OrderSubState.READY,
          Set.of(),
          1_000L);
    }

    @Override
    public List<StagedOrder> activeOrders() {
      return orders;
    }

    @Override
    public Optional<StagedOrder> findOrder(String orderId) {
      return orders.stream().filter(o -> o.orderId().equals(orderId)).findFirst();
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
    InMemoryAaaService aaa = new InMemoryAaaService(new InMemoryAaaEventLog());
    aaa.registerCredential("tok-a", "firm-a", "desk-1", "trader-a", Set.of());
    aaa.registerCredential("tok-b", "firm-a", "desk-1", "trader-b", Set.of());
    sessionA =
        ((LogonOutcome.Accepted) aaa.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "tok-a")))
            .session()
            .sessionId();
    sessionB =
        ((LogonOutcome.Accepted) aaa.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "tok-b")))
            .session()
            .sessionId();
    StagedOrderManager orders = new TwoOwnersOrders(sessionA, sessionB);
    SubscriptionRegistry subscriptions = new SubscriptionRegistry();
    ApiSurface api =
        new ApiSurface(
            aaa,
            orders,
            new InMemoryRouteManager(orders),
            subscriptions,
            (sid, subId, event) -> {});
    binding = new RestEdgeBinding(aaa, api, subscriptions);
    binding.setBlotterExport(orders);
  }

  @Test
  void exportsOnlyTheCallingSessionsOwnOrders() {
    RestEdgeBinding.HttpResult result =
        binding.handle(
            "GET",
            "/api/v1/blotter/export.csv",
            Map.of(),
            Map.of("x-ems-session", String.valueOf(sessionA)),
            "");
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.contentType()).isEqualTo("text/csv; charset=utf-8");
    assertThat(result.body()).contains("O-A").contains("ACC-A");
    assertThat(result.body()).doesNotContain("O-B").doesNotContain("ACC-B");
  }

  @Test
  void missingExportConfigurationIs404() throws Exception {
    InMemoryAaaService aaa = new InMemoryAaaService(new InMemoryAaaEventLog());
    aaa.registerCredential("tok-x", "firm-a", "desk-1", "trader-x", Set.of());
    long session =
        ((LogonOutcome.Accepted) aaa.logon(LogonCredentials.fresh(CredentialKind.TOKEN, "tok-x")))
            .session()
            .sessionId();
    SubscriptionRegistry subscriptions = new SubscriptionRegistry();
    RestEdgeBinding unconfigured =
        new RestEdgeBinding(
            aaa,
            new ApiSurface(
                aaa,
                new TwoOwnersOrders(session, session),
                new InMemoryRouteManager(new TwoOwnersOrders(session, session)),
                subscriptions,
                (sid, subId, event) -> {}),
            subscriptions);
    RestEdgeBinding.HttpResult result =
        unconfigured.handle(
            "GET",
            "/api/v1/blotter/export.csv",
            Map.of(),
            Map.of("x-ems-session", String.valueOf(session)),
            "");
    assertThat(result.status()).isEqualTo(404);
  }
}
