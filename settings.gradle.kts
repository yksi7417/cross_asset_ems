/*
 * Root Gradle settings for the cross-asset EMS Java multi-module build.
 *
 * Each module under java/ corresponds to an architectural layer documented
 * in 80_architecture/ and indexed in 00_index/architecture-index.md.
 */

rootProject.name = "cross-asset-ems"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    defaultLibrariesExtensionName = "projectLibs"
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("gradle/catalogs/libs.versions.toml"))
        }
    }
}

// Convention plugins live under build-logic/
includeBuild("build-logic")

// Java modules — keep in sync with java/README.md
include(
    ":ems-core",
    ":ems-fsm",
    ":ems-transport",
    ":ems-aaa",
    ":ems-validator",
    ":ems-oms",
    ":ems-fix-bridge",
    ":ems-market-data",
    ":ems-pretrade",
    ":ems-venue-connectivity",
    ":ems-posttrade",
    ":ems-observability",
    ":ems-ops",
    ":ems-bench",
    ":ems-it",
)

// Each Gradle subproject lives under java/<name>/
rootProject.children.forEach { module ->
    module.projectDir = file("java/${module.name}")
}
