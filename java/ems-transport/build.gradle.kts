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

// Run the toy interactively so you can inspect the archive before it closes.
// Usage: ./gradlew :ems-transport:runToy
// Then: hexdump -C /tmp/ems-aeron-demo/archive/*.log | grep -A1 "PING\|PONG"
tasks.register<JavaExec>("runToy") {
    group = "application"
    description = "Run AeronToyPingPong interactively (leaves archive in /tmp/ems-aeron-demo)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.crossasset.ems.transport.AeronToyPingPong")
    jvmArgs(
        "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED",
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
    )
    standardInput = System.`in`
}
