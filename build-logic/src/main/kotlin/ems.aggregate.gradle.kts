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
