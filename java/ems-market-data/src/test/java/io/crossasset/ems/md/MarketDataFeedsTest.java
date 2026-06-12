/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.md;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.crossasset.ems.md.bloomberg.BloombergFeed;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Feed selection: env-driven, defaulting to the simulator, loud on unknown values. */
class MarketDataFeedsTest {

  @Test
  void defaultsToTheSimulator() {
    MarketDataFeed feed = MarketDataFeeds.fromEnv(Map.of());
    assertThat(feed).isInstanceOf(SimulatedFeed.class);
    assertThat(MarketDataFeeds.isSimulated(feed)).isTrue();
  }

  @Test
  void bloombergDesktop_buildsTheBlpapiFeed() {
    MarketDataFeed feed = MarketDataFeeds.fromEnv(Map.of("EMS_MD_FEED", "bloomberg-desktop"));
    assertThat(feed).isInstanceOf(BloombergFeed.class);
    assertThat(MarketDataFeeds.isSimulated(feed)).isFalse();
    assertThat(feed.feedId()).isEqualTo("bloomberg-dapi");
  }

  @Test
  void bloombergServer_readsHostPortApp() {
    MarketDataFeed feed =
        MarketDataFeeds.fromEnv(
            Map.of(
                "EMS_MD_FEED", "bloomberg-server",
                "EMS_BBG_HOST", "sapi.example.com",
                "EMS_BBG_PORT", "8195",
                "EMS_BBG_APP", "ems-prod"));
    assertThat(feed).isInstanceOf(BloombergFeed.class);
    assertThat(feed.feedId()).isEqualTo("bloomberg-sapi");
  }

  @Test
  void unknownSelection_failsLoudly() {
    // A typo must never silently fall back to simulated prices.
    assertThatThrownBy(() -> MarketDataFeeds.fromEnv(Map.of("EMS_MD_FEED", "bloomberg")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sim | bloomberg-desktop | bloomberg-server");
  }
}
