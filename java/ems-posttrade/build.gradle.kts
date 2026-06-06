/*
 * ems-posttrade — Allocation + STP + Confirmation/Affirmation + Reg
 * reporting + Best-ex audit. See arch-allocation-service, arch-stp-pipeline,
 * arch-confirmation-affirmation, arch-regulatory-reporting-service,
 * arch-best-execution.
 */

plugins {
    id("ems.java-conventions")
}

dependencies {
    api(project(":ems-core"))
    api(project(":ems-oms"))
    implementation(project(":ems-pretrade"))
}
