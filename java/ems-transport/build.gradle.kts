/*
 * ems-transport — SBE encoding + Aeron transport (Cluster + Archive).
 *
 * See arch-sbe-aeron-transport and arch-resilience-24x7.
 */

plugins {
    id("ems.java-conventions")
}

dependencies {
    api(project(":ems-core"))
    api(libs.bundles.aeron)
    api(libs.aeron.cluster)
    api(libs.aeron.archive)
    implementation(libs.sbe.tool)
}
