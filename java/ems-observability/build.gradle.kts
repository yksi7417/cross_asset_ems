/*
 * ems-observability — OTel SDK + ELK ingest + Prometheus exporters.
 * See arch-observability.
 */

plugins {
    id("ems.java-conventions")
}

// Runnable toy for verifying the OTel collector + Jaeger pipeline end-to-end.
// Usage: ./gradlew :ems-observability:run   (or via scripts/dev/run-otel-toy.sh)
// Override main class: ./gradlew :ems-observability:run -PmainClass=com.example.Other
tasks.register<JavaExec>("run") {
    group = "application"
    description = "Run OtelToyTrace to smoke-test the OTel → collector → Jaeger pipeline."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = providers.gradleProperty("mainClass")
        .orElse("io.crossasset.ems.observability.OtelToyTrace")
}

dependencies {
    api(project(":ems-core"))
    api(libs.bundles.otel)
    api(libs.micrometer.core)
    implementation(libs.micrometer.prometheus)
}
