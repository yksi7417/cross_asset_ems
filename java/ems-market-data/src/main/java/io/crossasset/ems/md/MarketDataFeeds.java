/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.md;

import io.crossasset.ems.md.bloomberg.BloombergConfig;
import io.crossasset.ems.md.bloomberg.BloombergFeed;
import io.crossasset.ems.md.bloomberg.ReflectiveBlpapiDriver;
import java.util.Map;

/**
 * Market-data feed selection (the 18.13 Bloomberg adapter become reachable): one env-driven factory
 * so every edge/service picks its feed the same way, and the SAME feed instance powers both halves
 * — the GUI path (ticks → md topics → watchlist/blotter) and the backend path (benchmarks 9.5,
 * quote fabric 9.1/9.3, P&L marks).
 *
 * <pre>
 *   EMS_MD_FEED=sim                  # default: the deterministic simulated feed
 *   EMS_MD_FEED=bloomberg-desktop    # BLPAPI Desktop API, terminal on this machine (:8194)
 *   EMS_MD_FEED=bloomberg-server     # SAPI/B-PIPE: EMS_BBG_HOST / EMS_BBG_PORT / EMS_BBG_APP
 * </pre>
 *
 * <p>Bloomberg needs the desk's licensed blpapi jar on the classpath (Bloomberg does not publish it
 * to Maven); without it the feed comes up with health DOWN and a jar-install hint — visible on the
 * desktop's MARKET DATA chip rather than a silent fallback, because a desk that asked for Bloomberg
 * should never unknowingly trade off simulated prices.
 */
public final class MarketDataFeeds {

  private MarketDataFeeds() {}

  /** Build the feed selected by {@code env} (pass {@code System.getenv()} in production). */
  public static MarketDataFeed fromEnv(Map<String, String> env) {
    String selection = env.getOrDefault("EMS_MD_FEED", "sim");
    return switch (selection) {
      case "sim" -> new SimulatedFeed("sim");
      case "bloomberg-desktop" ->
          new BloombergFeed(BloombergConfig.desktop(), new ReflectiveBlpapiDriver());
      case "bloomberg-server" ->
          new BloombergFeed(
              BloombergConfig.server(
                  env.getOrDefault("EMS_BBG_HOST", "localhost"),
                  Integer.parseInt(env.getOrDefault("EMS_BBG_PORT", "8194")),
                  env.getOrDefault("EMS_BBG_APP", "ems")),
              new ReflectiveBlpapiDriver());
      default ->
          throw new IllegalArgumentException(
              "EMS_MD_FEED must be sim | bloomberg-desktop | bloomberg-server, got " + selection);
    };
  }

  /** True when the selected feed is the deterministic simulator (demo bot may emit ticks). */
  public static boolean isSimulated(MarketDataFeed feed) {
    return feed instanceof SimulatedFeed;
  }
}
