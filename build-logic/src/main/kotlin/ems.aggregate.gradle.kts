/*
 * Aggregate plugin applied at the root.
 *
 * Wires up convention-level tasks (allTests, build summary) without forcing
 * sub-modules to share a build.gradle.kts.
 */

tasks.register("allTests") {
    group = "verification"
    description = "Run tests across every module."
    dependsOn(subprojects.map { "${it.path}:test" })
}

tasks.register("phase0Smoke") {
    group = "verification"
    description = "Phase-0 acceptance gate — Aeron Cluster + Archive round-trip."
    dependsOn(":ems-transport:phase0Smoke")
}
