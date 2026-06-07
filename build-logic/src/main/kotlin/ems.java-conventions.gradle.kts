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
    val versionCatalog =
        project.extensions.getByType<VersionCatalogsExtension>().named("libs")

    "implementation"(versionCatalog.findLibrary("slf4j-api").get())
    "testImplementation"(versionCatalog.findBundle("test-common").get())
    "testRuntimeOnly"(versionCatalog.findLibrary("logback-classic").get())
    "testRuntimeOnly"(versionCatalog.findLibrary("junit-platform-launcher").get())
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
