/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.blotter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.crossasset.ems.aaa.AaaService;
import io.crossasset.ems.api.SubscriptionRegistry;
import io.crossasset.ems.fix.dropcopy.DropCopyService;
import io.crossasset.ems.oms.StagedOrder;
import io.crossasset.ems.oms.StagedOrderManager;
import java.util.Objects;
import java.util.Optional;

/**
 * Bridges the blotter fill stream into the drop-copy service (task 12.16): every {@code
 * blotter.fills} row is enriched with the originating order's account/cumQty/leavesQty (staged
 * order FSM context) and firm/desk (session identity) and mirrored to every matching subscription.
 * Read-only, always on -- a drop copy can never originate or block an order, so there is no
 * flag-gate here (unlike the compliance route guard).
 */
public final class DropCopyBridge {

  private final SubscriptionRegistry subscriptions;
  private final StagedOrderManager orders;
  private final AaaService aaa;
  private final DropCopyService dropCopy;
  private final ObjectMapper mapper = new ObjectMapper();

  public DropCopyBridge(
      SubscriptionRegistry subscriptions,
      StagedOrderManager orders,
      AaaService aaa,
      DropCopyService dropCopy) {
    this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
    this.orders = Objects.requireNonNull(orders, "orders");
    this.aaa = Objects.requireNonNull(aaa, "aaa");
    this.dropCopy = Objects.requireNonNull(dropCopy, "dropCopy");
  }

  /** Subscribe to the fill stream; call once at edge start-up. */
  public void attach() {
    subscriptions.subscribe(
        0L,
        BlotterPublisher.TOPIC_FILLS,
        Long.MAX_VALUE,
        (sid, sub, event) -> onFill(event.payload()));
  }

  private void onFill(String payload) {
    JsonNode row;
    try {
      row = mapper.readTree(payload);
    } catch (Exception e) {
      return; // malformed row: blotter payloads are factory-shaped, never fatal here
    }
    String orderId = row.path("orderId").asText(null);
    String execId = row.path("execId").asText(null);
    String figi = row.path("figi").asText(null);
    int side = row.path("side").asInt(0);
    long lastQty = row.path("lastQty").asLong(0L);
    long lastPx = row.path("lastPx").asLong(0L);
    long ts = row.path("ts").asLong(0L);

    if (orderId == null
        || orderId.isBlank()
        || execId == null
        || execId.isBlank()
        || figi == null
        || figi.isBlank()
        || lastQty <= 0
        || (side != 1 && side != 2)
        || ts <= 0) {
      return;
    }

    Optional<StagedOrder> order = orders.findOrder(orderId);
    if (order.isEmpty()) {
      return; // fill arrived for an order this bridge can't resolve context for -- skip, don't guess
    }
    var context = order.get().fsmContext();
    String account = context.account();
    var identity = aaa.sessionInfo(order.get().sessionId()).map(s -> s.identity()).orElse(null);
    String firm = identity != null ? identity.firmId() : null;
    String desk = identity != null ? identity.deskId() : null;
    if (account == null
        || account.isBlank()
        || firm == null
        || firm.isBlank()
        || desk == null
        || desk.isBlank()) {
      return; // no firm/desk/account to scope the copy by
    }

    dropCopy.onExecution(
        new DropCopyService.Execution(
            execId,
            orderId,
            account,
            desk,
            firm,
            figi,
            side,
            lastQty,
            lastPx,
            context.cumQty(),
            context.leavesQty(),
            ts));
  }
}
