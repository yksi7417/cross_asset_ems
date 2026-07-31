/*
 * ems-it — cross-module integration tests using in-process Aeron + SBE
 * mocks (~500 component tests per the arch-ddd-tdd test pyramid), and the
 * home of the `ems-slice` binary the polyglot conformance gate runs.
 *
 * `installDist` produces build/install/ems-slice/bin/ems-slice, which is the
 * path conformance/harness/run.sh looks for. See conformance/README.md.
 */

plugins {
    id("ems.java-conventions")
    application
}

application {
    mainClass.set("io.crossasset.ems.it.slice.SliceMain")
    applicationName = "ems-slice"
}

dependencies {
    implementation(project(":ems-core"))
    implementation(project(":ems-transport"))
    implementation(project(":ems-aaa"))
    implementation(project(":ems-validator"))
    implementation(project(":ems-fsm"))

    testImplementation(project(":ems-core"))
    testImplementation(project(":ems-fsm"))
    testImplementation(project(":ems-transport"))
    testImplementation(project(":ems-oms"))
    testImplementation(project(":ems-validator"))
    testImplementation(project(":ems-fix-bridge"))
    testImplementation(project(":ems-venue-connectivity"))
    testImplementation(project(":ems-posttrade"))
    testImplementation(project(":ems-observability"))
}
