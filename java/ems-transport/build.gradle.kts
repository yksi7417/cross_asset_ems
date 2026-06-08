/*
 * ems-transport — SBE encoding + Aeron transport (Cluster + Archive).
 *
 * See arch-sbe-aeron-transport and arch-resilience-24x7.
 */

plugins {
    id("ems.java-conventions")
    id("ems.sbe-codegen")
}

dependencies {
    api(project(":ems-core"))
    api(libs.bundles.aeron)
    api(libs.aeron.cluster)
    api(libs.aeron.archive)
    runtimeOnly(libs.logback.classic)
}

tasks.withType<Test>().configureEach {
    // Agrona uses jdk.internal.misc.Unsafe which requires module opens on Java 17+.
    jvmArgs(
        "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED",
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
    )
}

// Phase-0 smoke gate — runs only tests tagged "phase0-smoke".
// Invoked by the root phase0Smoke task and the dedicated CI job.
tasks.register<Test>("phase0Smoke") {
    group = "verification"
    description = "Phase-0 acceptance gate: Aeron Cluster + Archive round-trip."
    useJUnitPlatform { includeTags("phase0-smoke") }
    jvmArgs(
        "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED",
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
    )
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
}

// Run AeronToyPingPong and exit immediately after printing results (default).
// Pass --args='--wait' to block for archive inspection before shutdown.
//
// Usage (non-blocking):  ./gradlew :ems-transport:runToy
// Usage (blocking):      ./gradlew :ems-transport:runToy --args='--wait'
// Inspect after exit:    hexdump -C /tmp/ems-aeron-demo/archive/*.rec | grep -A1 "PING\|PONG"
// Exclude the Aeron demo entrypoint (AeronToyPingPong) from JaCoCo metrics.
// Its main() is a CLI entry point, not testable via unit tests; the real
// cluster behavior is covered by AeronToyPingPongTest integration tests.
// Also exclude SBE-generated codecs: auto-generated classes with no business logic to test.
val demoExcludes = listOf(
    "**/AeronToyPingPong.class",
    "**/io/crossasset/ems/schemas/**"
)

tasks.jacocoTestReport {
    classDirectories.setFrom(
        files(classDirectories.files.map { dir ->
            fileTree(dir) { exclude(demoExcludes) }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    classDirectories.setFrom(
        files(classDirectories.files.map { dir ->
            fileTree(dir) { exclude(demoExcludes) }
        })
    )
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.register<JavaExec>("runToy") {
    group = "application"
    description = "Run AeronToyPingPong (exits after results; use --args='--wait' to block for archive inspection)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.crossasset.ems.transport.AeronToyPingPong")
    jvmArgs(
        "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED",
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
    )
    standardInput = System.`in`
}
