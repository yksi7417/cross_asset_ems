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

// Manual desk smoke for the Bloomberg adapter (task 18.13). Requires a terminal/SAPI
// endpoint and the desk's licensed blpapi jar on the classpath (-PblpapiJar=/path/to/blpapi.jar):
//   ./gradlew :ems-market-data:runBloombergFeed -PbbgArgs="desktop BBG000BLNNH6"
tasks.register<JavaExec>("runBloombergFeed") {
    group = "application"
    description = "Run the Bloomberg feed smoke against a terminal/SAPI endpoint"
    classpath = sourceSets["main"].runtimeClasspath +
        files(project.findProperty("blpapiJar")?.toString() ?: emptyList<String>())
    mainClass.set("io.crossasset.ems.md.bloomberg.BloombergFeedMain")
    args((project.findProperty("bbgArgs")?.toString() ?: "desktop BBG000BLNNH6").split(" "))
}
