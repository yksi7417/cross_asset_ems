/*
 * ems-ops — JMX introspection + Time/Replay surface + Configuration
 * service. See arch-jmx-introspection, arch-time-replay-server,
 * arch-configuration-service.
 */

plugins {
    id("ems.java-conventions")
}

dependencies {
    api(project(":ems-core"))
    api(project(":ems-transport"))
}
