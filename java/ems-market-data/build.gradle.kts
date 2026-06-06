/*
 * ems-market-data — Quote server + IOI + real-time analytics (VWAP/PWP).
 * See arch-quote-server, arch-ioi, arch-realtime-analytics.
 */

plugins {
    id("ems.java-conventions")
}

dependencies {
    api(project(":ems-core"))
    api(project(":ems-transport"))
}
