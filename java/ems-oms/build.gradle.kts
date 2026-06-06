/*
 * ems-oms — Staged Order Manager + Router + Automation + Multi-leg/Package.
 * See arch-order-staged, arch-router-layer, arch-automation-layer,
 * arch-multileg, arch-aggregation, arch-fx-netting.
 */

plugins {
    id("ems.java-conventions")
}

dependencies {
    api(project(":ems-core"))
    api(project(":ems-fsm"))
    api(project(":ems-transport"))
    implementation(project(":ems-aaa"))
    implementation(project(":ems-validator"))
}
