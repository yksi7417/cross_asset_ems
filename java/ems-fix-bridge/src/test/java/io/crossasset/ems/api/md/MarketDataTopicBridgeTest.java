/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.md;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.crossasset.ems.api.ApiEvent;
import io.crossasset.ems.api.SubscriptionRegistry;
import io.crossasset.ems.md.FeedHealth;
import io.crossasset.ems.md.MdField;
import io.crossasset.ems.md.SimulatedFeed;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Bridge tests (task 18.12): SPI ticks become cursor-resumable {@link ApiEvent}s on the {@code md}
 * topics with flat JSON row payloads — the exact stream the Perspective desktop reads over the 8.10
 * REST/WS edge ({@code GET /api/v1/events?topic=md.{figi}&from=}).
 */
class MarketDataTopicBridgeTest {

  private static final String FIGI = "BBG000BLNNH6";

  private final ObjectMapper mapper = new ObjectMapper();
  private final SubscriptionRegistry registry = new SubscriptionRegistry();
  private final SimulatedFeed feed = new SimulatedFeed("sim");
  private final MarketDataTopicBridge bridge = new MarketDataTopicBridge(registry);

  @Test
  void tick_publishesJsonRowDelta_onFirehoseAndPerFigiTopics() throws Exception {
    bridge.attach(feed, FIGI, Set.of(MdField.BID, MdField.ASK));
    feed.emit(FIGI, Map.of(MdField.BID, 100_25L, MdField.ASK, 100_75L), 1_000L);

    for (String topic : List.of("md", "md." + FIGI)) {
      List<ApiEvent> events = registry.fetch(topic, 1, 10);
      assertThat(events).as("topic %s", topic).hasSize(1);
      ApiEvent event = events.get(0);
      assertThat(event.type()).isEqualTo("MdTick");
      assertThat(event.refId()).isEqualTo(FIGI);
      JsonNode row = mapper.readTree(event.payload());
      assertThat(row.get("figi").asText()).isEqualTo(FIGI);
      assertThat(row.get("feed").asText()).isEqualTo("sim");
      assertThat(row.get("ts").asLong()).isEqualTo(1_000L);
      assertThat(row.get("bid").asLong()).isEqualTo(100_25L);
      assertThat(row.get("ask").asLong()).isEqualTo(100_75L);
    }
  }

  @Test
  void cursorResume_fetchFromSeq_returnsOnlyNewRows() {
    bridge.attach(feed, FIGI, Set.of(MdField.LAST));
    feed.emit(FIGI, Map.of(MdField.LAST, 1L), 1L);
    feed.emit(FIGI, Map.of(MdField.LAST, 2L), 2L);
    feed.emit(FIGI, Map.of(MdField.LAST, 3L), 3L);

    List<ApiEvent> resumed = registry.fetch("md." + FIGI, 2, 10);

    assertThat(resumed).hasSize(2);
    assertThat(resumed.get(0).seq()).isEqualTo(2);
    assertThat(resumed.get(1).seq()).isEqualTo(3);
  }

  @Test
  void detach_stopsBridging() {
    String attachment = bridge.attach(feed, FIGI, Set.of(MdField.LAST));
    feed.emit(FIGI, Map.of(MdField.LAST, 1L), 1L);

    assertThat(bridge.detach(attachment)).isTrue();
    feed.emit(FIGI, Map.of(MdField.LAST, 2L), 2L);

    assertThat(registry.fetch("md." + FIGI, 1, 10)).hasSize(1);
    assertThat(bridge.detach(attachment)).isFalse();
  }

  @Test
  void feedHealth_publishesOnFeedTopic() throws Exception {
    bridge.attachHealth(feed);
    feed.start();
    feed.setHealth(FeedHealth.Status.DEGRADED, "entitlement gaps", 7_000L);

    List<ApiEvent> events = registry.fetch("md.feed.sim", 1, 10);
    // Registration replays current state (DOWN), then UP, then DEGRADED.
    assertThat(events).extracting(ApiEvent::type).containsOnly("FeedHealth");
    JsonNode last = mapper.readTree(events.get(events.size() - 1).payload());
    assertThat(last.get("feed").asText()).isEqualTo("sim");
    assertThat(last.get("status").asText()).isEqualTo("DEGRADED");
    assertThat(last.get("detail").asText()).isEqualTo("entitlement gaps");
    assertThat(last.get("ts").asLong()).isEqualTo(7_000L);
  }

  @Test
  void subscriptionError_publishesOnMdTopics() throws Exception {
    bridge.attach(feed, FIGI, Set.of(MdField.BID));
    feed.failSubscription(FIGI, "ENTITLEMENT", "denied");

    List<ApiEvent> events = registry.fetch("md." + FIGI, 1, 10);
    assertThat(events).hasSize(1);
    assertThat(events.get(0).type()).isEqualTo("MdSubscriptionError");
    JsonNode row = mapper.readTree(events.get(0).payload());
    assertThat(row.get("code").asText()).isEqualTo("ENTITLEMENT");
  }
}
