/*
 * ems-fsm — the shared FIX-compliant Finite State Machine.
 *
 * YAML definitions live in schemas/fsm/. Codegen output lands in
 * src/main/generated/ via the codegen tool. See arch-fix-fsm-design.
 */

plugins {
    id("ems.java-conventions")
}

dependencies {
    api(project(":ems-core"))
    implementation(libs.snakeyaml)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.yaml)
}
