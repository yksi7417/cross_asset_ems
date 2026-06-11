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
    api(project(":ems-market-data"))
    api(project(":ems-venue-connectivity"))
    api(project(":ems-observability"))
    api(project(":ems-aaa"))
    implementation(project(":ems-validator"))
    implementation(libs.quickfixj.core)
    implementation(libs.jackson.databind)
}

// Trader-desktop demo edge (task 18.1): REST :8484 + WS :8485 + scripted flow.
//   ./gradlew :ems-fix-bridge:runTraderEdge
tasks.register<JavaExec>("runTraderEdge") {
    group = "application"
    description = "Run the trader-desktop demo edge (REST 8484, WS 8485, scripted order flow)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.crossasset.ems.api.rest.TraderDesktopEdgeMain")
}

// Standalone FIX venue simulator (task 11.15) for manual conformance runs:
//   ./gradlew :ems-fix-bridge:runFixSimulator -PsimPort=9876
tasks.register<JavaExec>("runFixSimulator") {
    group = "application"
    description = "Run the standalone FIX venue simulator (default port 9876)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.crossasset.ems.fix.sim.FixSimulatorMain")
    args(project.findProperty("simPort")?.toString() ?: "9876")
}
