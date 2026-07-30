/*
 * Common Java conventions applied by every Java module.
 *
 * Sourced once here so module build scripts stay minimal.
 */

import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    `java-library`
    jacoco
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
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

    // ── ErrorProne + NullAway ────────────────────────────────────────────────
    // See docs/decisions/0004-defensive-gate-stack.md §A (Java).
    //
    // The tree already annotates with org.jspecify and nothing checked it until
    // now. NullAway is the check that makes those annotations load-bearing.
    options.errorprone {
        // Generated FSM sources are not hand-maintainable, so they are not
        // held to hand-written-code standards. The generator is the thing
        // under review; its output is regenerated and diffed by the fsm-sync
        // gate step instead.
        excludedPaths.set(".*/generated/.*")
        disableWarningsInGeneratedCode.set(true)

        // ErrorProne's ERROR-severity checks stay blocking. Its advisory
        // WARNING-severity checks are off: javac runs with -Werror, so leaving
        // them on would turn every advisory suggestion into a build break and
        // the predictable response is to disable ErrorProne wholesale.
        disableAllWarnings.set(true)

        error("NullAway")
        // OnlyNullMarked: NullAway checks exactly the packages that declare
        // themselves null-correct with JSpecify's @NullMarked, and ignores the
        // rest.
        //
        // The alternative — NullAway:AnnotatedPackages=io.crossasset.ems —
        // turns the whole 684-file tree on at once, which produces hundreds of
        // errors in code that never claimed to be null-correct. That is not a
        // gate, it is an outage. Coverage instead grows one package at a time
        // and cannot shrink: scripts/ci/checks/nullmarked_ratchet.py fails the
        // build if a package that was @NullMarked stops being it.
        //
        // Every module ported for the polyglot slice is @NullMarked from its
        // first commit. See docs/decisions/0004-defensive-gate-stack.md.
        option("NullAway:OnlyNullMarked", "true")
    }
}

tasks.named<JavaCompile>("compileTestJava") {
    // Tests deliberately pass nulls to assert that production code rejects them.
    options.errorprone.disable("NullAway")
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

    "errorprone"(versionCatalog.findLibrary("errorprone-core").get())
    "errorprone"(versionCatalog.findLibrary("nullaway").get())

    // JSpecify annotations are part of the public API surface of every module —
    // @NullMarked on a package is visible to consumers, so `api`, not
    // `implementation`.
    "api"(versionCatalog.findLibrary("jspecify").get())

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

// ── JaCoCo ───────────────────────────────────────────────────────────────────

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// jacocoTestCoverageVerification: called explicitly from the pre-commit hook,
// NOT wired into :check so CI is not blocked on modules with no tests yet.
// Threshold is 60% line / 50% branch — calibrated against ems-fsm baseline.
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.60".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "BRANCH"
                minimum = "0.50".toBigDecimal()
            }
        }
    }
}
