/*
 * ems-market-data — Pluggable feed SPI (18.12: MarketDataFeed + SimulatedFeed);
 * later the quote server + IOI + real-time analytics (Phase 9, deferred).
 * See arch-quote-server, arch-ioi, arch-realtime-analytics.
 */

plugins {
    id("ems.java-conventions")
}

dependencies {
    api(project(":ems-core"))
    api(project(":ems-transport"))
}
