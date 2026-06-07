/*
 * Common Java conventions applied by every Java module.
 *
 * Sourced once here so module build scripts stay minimal.
 */

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    `java-library`
    jacoco
    id("com.diffplug.spotless")
}

val emsJavaVersion: String by project

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(emsJavaVersion.toInt()))
    }
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(
        listOf(
            "-Werror",
            "-Xlint:all",
            "-Xlint:-processing",
            "-parameters",
        )
    )
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events(
            TestLogEvent.PASSED,
            TestLogEvent.FAILED,
            TestLogEvent.SKIPPED,
        )
        exceptionFormat = TestExceptionFormat.FULL
        showStackTraces = true
    }
    // Hot-path components MUST run without allocation in steady state.
    // Bench runs separately under :ems-bench.
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.16")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.3")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.11.3")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testRuntimeOnly("ch.qos.logback:logback-classic:1.5.12")
}

spotless {
    java {
        target("src/**/*.java")
        targetExclude("**/generated/**")
        googleJavaFormat("1.24.0")
        removeUnusedImports()
        formatAnnotations()
        endWithNewline()
    }
}
