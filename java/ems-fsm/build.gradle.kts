/*
 * ems-fsm — the shared FIX-compliant Finite State Machine.
 *
 * YAML definitions live in schemas/fsm/. Codegen is driven by
 * tools/codegen/fsm_codegen.py and outputs to src/main/generated/.
 * Generated sources are committed so reviewers can diff them and CI
 * stays hermetic (no system Python required for compileJava).
 *
 * To regenerate after editing FSM YAML:
 *   ./gradlew fsmCodegen
 * CI verifies sources are in sync via the fsm-sync-check step.
 * See arch-fix-fsm-design.
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

val fsmSchemas = rootProject.layout.projectDirectory.dir("schemas/fsm")
val fsmCodegenScript = rootProject.layout.projectDirectory.file("tools/codegen/fsm_codegen.py")
// Generated sources live in src/main/generated/ and are committed.
val fsmGeneratedSrc = layout.projectDirectory.dir("src/main/generated")

// Manual regeneration task — NOT wired to compileJava so CI needs no Python.
// Run: ./gradlew fsmCodegen  (after editing schemas/fsm/*.fsm.yaml)
val fsmCodegen by tasks.registering(Exec::class) {
    group = "build"
    description = "Regenerate committed Java FSM sources from YAML definitions."

    inputs.dir(fsmSchemas)
    inputs.file(fsmCodegenScript)
    outputs.dir(fsmGeneratedSrc)

    commandLine("python3", fsmCodegenScript.asFile.absolutePath, "--java-only")
    workingDir = rootProject.projectDir
}

// Wire committed generated sources into the main source set.
sourceSets.named("main") {
    java.srcDir(fsmGeneratedSrc)
}
