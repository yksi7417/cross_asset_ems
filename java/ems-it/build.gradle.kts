/*
 * ems-it — cross-module integration tests using in-process Aeron + SBE
 * mocks. ~500 component tests per the arch-ddd-tdd test pyramid.
 */

plugins {
    id("ems.java-conventions")
}

dependencies {
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
