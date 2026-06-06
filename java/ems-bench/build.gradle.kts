/*
 * ems-bench — JMH-based performance benchmarks for the hot path.
 * Not part of the default build pipeline; run on demand.
 */

plugins {
    id("ems.java-conventions")
    id("me.champeau.jmh") version "0.7.2"
}

dependencies {
    implementation(project(":ems-core"))
    implementation(project(":ems-transport"))
    implementation(project(":ems-fsm"))
}
