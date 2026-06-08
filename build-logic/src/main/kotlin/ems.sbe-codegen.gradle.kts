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

        // Instrument schemas use hex IDs (id="0x...") which sbe-tool 1.34.1 rejects.
        // They also use mixed 2016/2017 namespace dialects. Exclude until they are
        // updated to decimal IDs; they will be wired in during Phase 4 tasks.
        val xmls = sbeSchemasDir.asFile
            .walkTopDown()
            .filter { it.isFile && it.name.endsWith(".xml")
                && !it.name.endsWith("-instrument.xml")
                && it.name != "envelope.xml" }
            .toList()
        if (xmls.isEmpty()) {
            logger.warn("No SBE schemas in {}; codegen task is a no-op.", sbeSchemasDir.asFile)
            args(emptyList<String>())
        } else {
            args(xmls.map { it.absolutePath })
        }
    }
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
