/*
 * SBE codegen convention plugin — no plugins{} block, see implementation note below.
 */

val sbeSchemasDir = rootProject.layout.projectDirectory.dir("schemas/sbe")
val generatedSourcesDir = layout.buildDirectory.dir("generated/sources/sbe")

val sbeCodegenClasspath: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    add("sbeCodegenClasspath", "uk.co.real-logic:sbe-tool:1.34.1")
}

// Resolve schema paths at configuration time (plain File — serializable).
// Instrument schemas use hex IDs / 2016 namespace dialect that sbe-tool 1.34.1
// rejects; exclude them until Phase-4 tasks bring them to decimal IDs.
val schemasDir: java.io.File = sbeSchemasDir.asFile
val outDir: java.io.File = generatedSourcesDir.get().asFile
val xmlArgs: List<String> = schemasDir
    .walkTopDown()
    .filter { it.isFile && it.name.endsWith(".xml")
        && !it.name.endsWith("-instrument.xml")
        && it.name != "envelope.xml" }
    .map { it.absolutePath }
    .sorted()
    .toList()

val sbeCodegen = tasks.register("sbeCodegen", JavaExec::class) {
    group = "build"
    description = "Generate Java codecs from SBE XML schemas."
    classpath = sbeCodegenClasspath
    mainClass.set("uk.co.real_logic.sbe.SbeTool")

    // Declaring inputs/outputs lets Gradle skip codegen when schemas are unchanged.
    inputs.dir(schemasDir)
    outputs.dir(outDir)

    systemProperty("sbe.output.dir", outDir.absolutePath)
    systemProperty("sbe.generate.ir", "false")
    systemProperty("sbe.target.language", "Java")
    systemProperty("sbe.java.generate.interfaces", "true")

    if (xmlArgs.isEmpty()) {
        logger.warn("No SBE schemas eligible for codegen in {}", schemasDir)
    }
    args(xmlArgs)
    // outDir is declared as an output; Gradle cleans stale outputs automatically.
    // No doFirst needed — any lambda in a convention plugin captures the script
    // class reference which configuration cache cannot serialize.
    outDir.mkdirs()
}

project.extensions.getByType(org.gradle.api.tasks.SourceSetContainer::class.java)
    .named("main")
    .configure { java.srcDir(generatedSourcesDir) }

afterEvaluate {
    tasks.named("compileJava") {
        dependsOn(sbeCodegen)
    }
    // sourcesJar also includes the generated source tree; declare the dependency
    // so Gradle's task-validation doesn't reject the implicit output usage.
    tasks.findByName("sourcesJar")?.dependsOn(sbeCodegen)
}

tasks.named("clean") {
    doLast {
        generatedSourcesDir.get().asFile.deleteRecursively()
    }
}
