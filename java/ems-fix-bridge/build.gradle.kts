/*
 * ems-fix-bridge — FIX gateway (in/out) + REST API + Bulk I/O (Excel/CSV).
 * See arch-fix-api-bridge, arch-api-first, arch-bulk-io.
 */

plugins {
    id("ems.java-conventions")
}

dependencies {
    api(project(":ems-core"))
    api(project(":ems-oms"))
    api(project(":ems-venue-connectivity"))
    implementation(project(":ems-aaa"))
    implementation(project(":ems-validator"))
    implementation(libs.quickfixj.core)
    implementation(libs.jackson.databind)
}
