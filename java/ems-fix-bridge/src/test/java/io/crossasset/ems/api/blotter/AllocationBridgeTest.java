/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.blotter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.crossasset.ems.api.SubscriptionRegistry;
import io.crossasset.ems.posttrade.allocation.AccountShare;
import io.crossasset.ems.posttrade.allocation.AllocationPolicy;
import io.crossasset.ems.posttrade.allocation.AllocationTemplate;
import io.crossasset.ems.posttrade.allocation.InMemoryAllocationService;
import io.crossasset.ems.posttrade.allocation.RoundingPolicy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Fill stream in, allocation event stream out — the wiring is one subscription each way. */
class AllocationBridgeTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final SubscriptionRegistry subscriptions = new SubscriptionRegistry();
  private final List<JsonNode> allocationRows = new ArrayList<>();

  private static final AllocationTemplate FIFTY_FIFTY =
      AllocationTemplate.of(
          "TPL-5050",
          1L,
          AllocationPolicy.PRO_RATA,
          RoundingPolicy.DISTRIBUTE_RESIDUAL,
          List.of(
              new AccountShare("ACC-A", "PB-1", 5_000L),
              new AccountShare("ACC-B", "PB-2", 5_000L)));

  @BeforeEach
  void setUp() {
    subscriptions.subscribe(
        0L,
        AllocationBridge.TOPIC_ALLOCATIONS,
        Long.MAX_VALUE,
        (sid, sub, event) -> {
          try {
            allocationRows.add(mapper.readTree(event.payload()));
          } catch (Exception e) {
            throw new AssertionError(e);
          }
        });
  }

  private void publishFill(String execId, String orderId, long qty, long px) {
    var row = mapper.createObjectNode();
    row.put("execId", execId);
    row.put("orderId", orderId);
    row.put("routeId", "RT-1");
    row.put("figi", "BBG000BLNNH6");
    row.put("side", 1);
    row.put("lastQty", qty);
    row.put("lastPx", px);
    subscriptions.publish(BlotterPublisher.TOPIC_FILLS, "FillRow", execId, row.toString());
  }

  @Test
  void fillSplitsAcrossTheTemplateAccounts() {
    new AllocationBridge(subscriptions, new InMemoryAllocationService(), o -> FIFTY_FIFTY).attach();
    publishFill("EX-1", "O-1", 101L, 25_00L);

    List<JsonNode> applied =
        allocationRows.stream().filter(r -> r.path("type").asText().equals("applied")).toList();
    assertThat(applied).hasSize(2);
    assertThat(applied.stream().mapToLong(r -> r.path("qty").asLong()).sum()).isEqualTo(101L);
    assertThat(applied.stream().map(r -> r.path("account").asText()))
        .containsExactlyInAnyOrder("ACC-A", "ACC-B");
    // the requested event opens the trail with the template identity
    assertThat(allocationRows.get(0).path("type").asText()).isEqualTo("requested");
    assertThat(allocationRows.get(0).path("templateId").asText()).isEqualTo("TPL-5050");
  }

  @Test
  void deferredTemplateParksTheFill() {
    new AllocationBridge(
            subscriptions,
            new InMemoryAllocationService(),
            o -> AllocationTemplate.deferred("TPL-LATER"))
        .attach();
    publishFill("EX-2", "O-2", 50L, 10_00L);

    assertThat(allocationRows).hasSize(1);
    assertThat(allocationRows.get(0).path("type").asText()).isEqualTo("deferred");
    assertThat(allocationRows.get(0).path("fillId").asText()).isEqualTo("EX-2");
  }

  @Test
  void malformedFillRowIsIgnoredNotFatal() {
    new AllocationBridge(subscriptions, new InMemoryAllocationService(), o -> FIFTY_FIFTY).attach();
    subscriptions.publish(BlotterPublisher.TOPIC_FILLS, "FillRow", "junk", "{not json");
    assertThat(allocationRows).isEmpty();
  }
}
