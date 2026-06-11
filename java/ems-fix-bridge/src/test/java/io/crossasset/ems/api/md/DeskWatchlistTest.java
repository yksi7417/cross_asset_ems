/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.md;

import static org.assertj.core.api.Assertions.assertThat;

import io.crossasset.ems.api.ApiEvent;
import io.crossasset.ems.api.SubscriptionRegistry;
import io.crossasset.ems.md.MdField;
import io.crossasset.ems.md.SimulatedFeed;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Per-desk watchlist tests (task 18.14): add attaches the symbol to the md bridge and publishes a
 * WatchRow delta on watchlist.{desk}; remove detaches (ticks stop) and publishes WatchRemoved; a
 * replay from seq 1 rebuilds the desk's set; desks are independent.
 */
class DeskWatchlistTest {

  private static final String FIGI = "BBG000B9XRY4";
  private static final Set<MdField> FIELDS = Set.of(MdField.BID, MdField.ASK);

  private final SubscriptionRegistry registry = new SubscriptionRegistry();
  private final SimulatedFeed feed = new SimulatedFeed("sim");
  private final MarketDataTopicBridge bridge = new MarketDataTopicBridge(registry);
  private final DeskWatchlist watchlist = new DeskWatchlist(registry, bridge, feed, FIELDS);

  @Test
  void add_publishesWatchRow_andTicksStartFlowing() {
    assertThat(watchlist.add("desk-1", FIGI)).isTrue();
    assertThat(watchlist.add("desk-1", FIGI)).as("duplicate add").isFalse();

    List<ApiEvent> watchEvents = registry.fetch("watchlist.desk-1", 1, 10);
    assertThat(watchEvents).hasSize(1);
    assertThat(watchEvents.get(0).type()).isEqualTo("WatchRow");
    assertThat(watchEvents.get(0).refId()).isEqualTo(FIGI);

    feed.emit(FIGI, Map.of(MdField.BID, 100L), 1L);
    assertThat(registry.fetch("md." + FIGI, 1, 10)).hasSize(1);
    assertThat(watchlist.list("desk-1")).containsExactly(FIGI);
  }

  @Test
  void remove_publishesWatchRemoved_andTicksStop() {
    watchlist.add("desk-1", FIGI);
    assertThat(watchlist.remove("desk-1", FIGI)).isTrue();
    assertThat(watchlist.remove("desk-1", FIGI)).as("double remove").isFalse();

    feed.emit(FIGI, Map.of(MdField.BID, 100L), 1L);
    assertThat(registry.fetch("md." + FIGI, 1, 10)).isEmpty();

    List<ApiEvent> watchEvents = registry.fetch("watchlist.desk-1", 1, 10);
    assertThat(watchEvents).extracting(ApiEvent::type).containsExactly("WatchRow", "WatchRemoved");
    assertThat(watchlist.list("desk-1")).isEmpty();
  }

  @Test
  void desksAreIndependent() {
    watchlist.add("desk-1", FIGI);
    watchlist.add("desk-2", "BBG000BPH459");

    assertThat(watchlist.list("desk-1")).containsExactly(FIGI);
    assertThat(watchlist.list("desk-2")).containsExactly("BBG000BPH459");
    assertThat(registry.fetch("watchlist.desk-1", 1, 10)).hasSize(1);
    assertThat(registry.fetch("watchlist.desk-2", 1, 10)).hasSize(1);

    watchlist.remove("desk-1", FIGI);
    assertThat(watchlist.list("desk-2")).as("desk-2 unaffected").hasSize(1);
  }

  @Test
  void replayFromSeq1_rebuildsTheSet() {
    watchlist.add("desk-1", FIGI);
    watchlist.add("desk-1", "BBG000BPH459");
    watchlist.remove("desk-1", FIGI);

    // The panel replays the topic and applies WatchRow/WatchRemoved in order.
    var set = new java.util.LinkedHashSet<String>();
    for (ApiEvent event : registry.fetch("watchlist.desk-1", 1, 100)) {
      if ("WatchRow".equals(event.type())) {
        set.add(event.refId());
      } else if ("WatchRemoved".equals(event.type())) {
        set.remove(event.refId());
      }
    }
    assertThat(set).containsExactly("BBG000BPH459");
    assertThat(set).isEqualTo(watchlist.list("desk-1"));
  }
}
