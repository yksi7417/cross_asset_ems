/*
 * Root build script. Convention plugins live in build-logic/.
 *
 * Per-module build scripts apply the conventions; this root file is
 * deliberately minimal so subprojects can be reasoned about independently.
 */

plugins {
    id("ems.aggregate")
}

group = "io.crossasset.ems"
version = "0.0.1-SNAPSHOT"

tasks.register("printSummary") {
    doLast {
        val modules = subprojects.size
        println("Cross-Asset EMS — $modules modules")
        subprojects.forEach { println("  - ${it.path}") }
    }
}
