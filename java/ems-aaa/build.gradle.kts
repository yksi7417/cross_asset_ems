/*
 * ems-aaa — Authentication / Authorization / Accounting + identity chain
 * + session sequence recovery. See entry-point-aaa, arch-firm-desk-user,
 * arch-tag-permissions, arch-identity-chaining, arch-sequence-recovery.
 */

plugins {
    id("ems.java-conventions")
}

dependencies {
    api(project(":ems-core"))
    api(project(":ems-transport"))
}
