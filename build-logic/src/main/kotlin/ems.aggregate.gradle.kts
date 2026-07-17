/*
 * Aggregate plugin applied at the root.
 *
 * Wires up convention-level tasks (allTests, build summary) without forcing
 * sub-modules to share a build.gradle.kts.
 */

plugins {
    jacoco
}

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

// ── Aggregated coverage ───────────────────────────────────────────────────────
// Unions every module's per-test exec data into a single JaCoCo report so total
// line/branch coverage is visible at a glance (and consumable by CI). Generated
// FSM sources are excluded — they are codegen, not hand-written.
tasks.register<JacocoReport>("jacocoRootReport") {
    group = "verification"
    description = "Aggregated JaCoCo coverage across every module."

    // Run each module's tests first so exec data exists.
    dependsOn(subprojects.map { "${it.path}:test" })

    executionData.setFrom(
        files(
            subprojects.map { it.layout.buildDirectory.file("jacoco/test.exec") }
        ).filter { it.exists() }
    )

    sourceDirectories.setFrom(
        files(subprojects.map { it.file("src/main/java") })
    )

    classDirectories.setFrom(
        files(
            subprojects.map { it.layout.buildDirectory.dir("classes/java/main") }
        ).asFileTree.matching { exclude("**/generated/**") }
    )

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
        xml.outputLocation.set(
            layout.buildDirectory.file("reports/jacoco/aggregate/jacocoRootReport.xml")
        )
        html.outputLocation.set(
            layout.buildDirectory.dir("reports/jacoco/aggregate/html")
        )
    }
}
