/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.blotter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.crossasset.ems.api.SubscriptionRegistry;
import io.crossasset.ems.posttrade.allocation.AllocationEvent;
import io.crossasset.ems.posttrade.allocation.AllocationService;
import io.crossasset.ems.posttrade.allocation.AllocationTemplate;
import io.crossasset.ems.posttrade.allocation.Fill;
import java.util.Objects;
import java.util.function.Function;

/**
 * Bridges the blotter fill stream into the allocation pipeline (arch-allocation-service): every
 * {@code blotter.fills} row is allocated through the order's template and the resulting
 * event-sourced {@link AllocationEvent}s publish on {@code blotter.allocations} — downstream
 * (position keeping, STP, reg reporting) projects from that stream exactly like the UI does from
 * the blotter topics.
 */
public final class AllocationBridge {

  public static final String TOPIC_ALLOCATIONS = "blotter.allocations";

  private final SubscriptionRegistry subscriptions;
  private final AllocationService allocations;
  private final Function<String, AllocationTemplate> templateForOrder;
  private final ObjectMapper mapper = new ObjectMapper();

  public AllocationBridge(
      SubscriptionRegistry subscriptions,
      AllocationService allocations,
      Function<String, AllocationTemplate> templateForOrder) {
    this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
    this.allocations = Objects.requireNonNull(allocations, "allocations");
    this.templateForOrder = Objects.requireNonNull(templateForOrder, "templateForOrder");
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
    Fill fill =
        new Fill(
            row.path("execId").asText(),
            row.path("orderId").asText(),
            row.path("routeId").asText(),
            row.path("lastQty").asLong(),
            row.path("lastPx").asLong());
    AllocationTemplate template = templateForOrder.apply(fill.orderId());
    for (AllocationEvent event : allocations.allocate(fill, template)) {
      publish(event);
    }
  }

  private void publish(AllocationEvent event) {
    ObjectNode node = mapper.createObjectNode();
    node.put("fillId", event.fillId());
    switch (event) {
      case AllocationEvent.AllocationRequested r -> {
        node.put("type", "requested");
        node.put("templateId", r.templateId());
        node.put("templateVersion", r.templateVersion());
        node.put("policy", r.policy().name());
        node.put("rounding", r.rounding().name());
      }
      case AllocationEvent.AllocationApplied a -> {
        node.put("type", "applied");
        node.put("allocationId", a.allocationId());
        node.put("account", a.account());
        node.put("primeBroker", a.primeBroker());
        node.put("qty", a.qty());
        node.put("price", a.price());
        node.put("settleTarget", a.settleTarget());
      }
      case AllocationEvent.AllocationDeferred d -> {
        node.put("type", "deferred");
        node.put("reason", d.reason());
      }
      case AllocationEvent.AllocationReversed r -> {
        node.put("type", "reversed");
        node.put("originalAllocationId", r.originalAllocationId());
        node.put("reason", r.reason());
      }
      case AllocationEvent.AllocationAnomaly a -> {
        node.put("type", "anomaly");
        node.put("reason", a.reason());
        node.put("suggestedAction", a.suggestedAction());
      }
    }
    subscriptions.publish(TOPIC_ALLOCATIONS, "AllocationRow", event.fillId(), node.toString());
  }
}
