/*
 * ems-venue-connectivity — Venue adapter framework + per-venue adapters
 * + SOR + RFQ orchestration. See arch-venue-connectivity,
 * arch-smart-order-router, arch-rfq.
 */

plugins {
    id("ems.java-conventions")
}

dependencies {
    api(project(":ems-core"))
    api(project(":ems-oms"))
    implementation(project(":ems-market-data"))
    implementation(project(":ems-pretrade"))
    implementation(libs.quickfixj.core)
}
