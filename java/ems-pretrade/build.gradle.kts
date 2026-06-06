/*
 * ems-pretrade — Compliance + Risk + Position + Pricing + Pre-trade analytics.
 * See arch-compliance, arch-risk-engine, arch-position-service,
 * arch-pricing-service, arch-pretrade-analytics.
 */

plugins {
    id("ems.java-conventions")
}

dependencies {
    api(project(":ems-core"))
    api(project(":ems-oms"))
    implementation(project(":ems-market-data"))
    implementation(project(":ems-aaa"))
}
