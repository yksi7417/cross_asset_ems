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
}

tasks.withType<Test>().configureEach {
    // Agrona uses jdk.internal.misc.Unsafe which requires module opens on Java 17+.
    jvmArgs(
        "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED",
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
    )
}
