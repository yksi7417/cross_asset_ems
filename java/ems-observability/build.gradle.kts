/*
 * ems-observability — OTel SDK + ELK ingest + Prometheus exporters.
 * See arch-observability.
 */

plugins {
    id("ems.java-conventions")
}

dependencies {
    api(project(":ems-core"))
    api(libs.bundles.otel)
    api(libs.micrometer.core)
    implementation(libs.micrometer.prometheus)
}
