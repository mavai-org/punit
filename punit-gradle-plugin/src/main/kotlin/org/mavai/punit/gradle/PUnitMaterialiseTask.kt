package org.mavai.punit.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path

/**
 * The `mavaiMaterialise` task: graduation's source projection. For each
 * `mavai-contract/1` file under the test resource roots, emits the
 * equivalent contract as Java source the developer owns — same
 * criteria, same thresholds, a stub invocation, TODOs where the
 * reader's private machinery becomes the developer's code — under
 * `build/punit/materialised/`. One-shot scaffolding: the output is a
 * starting point to copy into the source tree, never compiled by the
 * build and never round-tripped.
 */
@DisableCachingByDefault(because = "One-shot scaffolding; the output is a starting point, not a build artefact")
abstract class PUnitMaterialiseTask : DefaultTask() {

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contractFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Classpath
    abstract val materialiseClasspath: ConfigurableFileCollection

    @TaskAction
    fun materialise() {
        val contracts = contractFiles.files
            .filter { it.isFile && it.name.endsWith(".yaml") && isContract(it) }
            .sortedBy { it.absolutePath }
        if (contracts.isEmpty()) {
            logger.lifecycle("mavaiMaterialise: no mavai-contract/1 files under the test resource roots")
            return
        }
        val urls = materialiseClasspath.files
            .filter { it.exists() }
            .map { it.toURI().toURL() }
            .toTypedArray()
        val classLoader = URLClassLoader(urls, ClassLoader.getSystemClassLoader())
        try {
            val entry = classLoader.loadClass("org.mavai.punit.decl.ContractMaterialise")
            val run = entry.getMethod("run", Path::class.java)
            val className = entry.getMethod("className", String::class.java)
            val target = outputDir.get().asFile
            target.mkdirs()
            for (contract in contracts) {
                val source = run.invoke(null, contract.toPath()) as String
                val id = contract.useLines { lines ->
                    lines.first { it.startsWith("contract:") }.substringAfter(":").trim()
                }
                val file = File(target, "${className.invoke(null, id)}.java")
                file.writeText(source)
                logger.lifecycle("materialised ${contract.name} -> ${file.relativeTo(project.projectDir)}")
            }
        } finally {
            classLoader.close()
        }
    }

    private fun isContract(file: File): Boolean =
        file.useLines { lines -> lines.take(20).any { it.trim() == "format: mavai-contract/1" } }
}
