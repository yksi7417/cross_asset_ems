/*
 * SBE codegen convention plugin.
 *
 * Applied by any module that needs to consume SBE schemas. Reads from
 * schemas/sbe/*.xml at the repo root and emits Java sources into
 * build/generated/sources/sbe/.
 *
 * Per arch-sbe-aeron-transport: schemas are the contract surface.
 * Generated code is also committed under src/main/generated/ so reviewers
 * can see what consumers actually see; this task overwrites that
 * directory when the schemas change.
 */

plugins {
    id("ems.java-conventions")
}

val sbeSchemasDir = rootProject.layout.projectDirectory.dir("schemas/sbe")
val generatedSourcesDir = layout.buildDirectory.dir("generated/sources/sbe")

val sbeCodegenClasspath: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    sbeCodegenClasspath(
        project.extensions.getByType<VersionCatalogsExtension>()
            .named("libs").findLibrary("sbe-tool").get()
    )
}

val sbeCodegen = tasks.register("sbeCodegen", JavaExec::class) {
    group = "build"
    description = "Generate Java codecs from SBE XML schemas."
    classpath = sbeCodegenClasspath
    mainClass.set("uk.co.real_logic.sbe.SbeTool")

    val outDir = generatedSourcesDir.get().asFile

    inputs.dir(sbeSchemasDir)
    outputs.dir(outDir)

    systemProperty("sbe.output.dir", outDir.absolutePath)
    systemProperty("sbe.generate.ir", "false")
    systemProperty("sbe.target.language", "Java")
    systemProperty("sbe.java.generate.interfaces", "true")

    doFirst {
        println("SBE Codegen starting. Output dir: ${outDir.absolutePath}")
        outDir.deleteRecursively()
        outDir.mkdirs()

        val xmls = sbeSchemasDir.asFile
            .walkTopDown()
            .filter { it.isFile && it.name.endsWith(".xml") }
            .toList()
        if (xmls.isEmpty()) {
            logger.warn("No SBE schemas in {}; codegen task is a no-op.", sbeSchemasDir.asFile)
            args(emptyList<String>())
        } else {
            args(xmls.map { it.absolutePath })
        }
    }
}

// Wire generated sources into the main compile graph.
sourceSets.named("main") {
    java.srcDir(generatedSourcesDir)
}

afterEvaluate {
    tasks.named<JavaCompile>("compileJava") {
        dependsOn(sbeCodegen)
    }
}

tasks.named("clean") {
    doLast {
        generatedSourcesDir.get().asFile.deleteRecursively()
    }
}
