/*
 * ems-fsm — the shared FIX-compliant Finite State Machine.
 *
 * YAML definitions live in schemas/fsm/. Codegen is driven by
 * tools/codegen/fsm_codegen.py and outputs to src/main/generated/.
 * Generated sources are committed so reviewers can diff them.
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
val fsmGeneratedSrc = layout.buildDirectory.dir("generated/sources/fsm")

val fsmCodegen by tasks.registering(Exec::class) {
    group = "build"
    description = "Regenerate Java FSM sources from YAML definitions."

    inputs.dir(fsmSchemas)
    inputs.file(fsmCodegenScript)
    outputs.dir(fsmGeneratedSrc)

    // Pass output dir via env so the script knows where to write
    environment("FSM_JAVA_OUT", fsmGeneratedSrc.get().asFile.absolutePath)
    commandLine("python3", fsmCodegenScript.asFile.absolutePath, "--java-only")
    workingDir = rootProject.projectDir
}

// Wire generated sources into the main source set and compilation.
sourceSets.named("main") {
    java.srcDir(fsmGeneratedSrc)
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(fsmCodegen)
}
