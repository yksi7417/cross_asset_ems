/*
 * ems-validator — single source of hard reject with standardized codes.
 * See arch-validator.
 */

plugins {
    id("ems.java-conventions")
}

dependencies {
    api(project(":ems-core"))
    api(project(":ems-transport"))
    implementation(project(":ems-aaa"))
}
