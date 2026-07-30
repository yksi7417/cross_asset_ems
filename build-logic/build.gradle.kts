plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.javapoet)
    implementation("com.diffplug.spotless:com.diffplug.spotless.gradle.plugin:6.25.0")
    // ErrorProne + NullAway — see docs/decisions/0004-defensive-gate-stack.md.
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:4.0.1")
}
