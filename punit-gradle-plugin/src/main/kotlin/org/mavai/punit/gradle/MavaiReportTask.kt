package org.mavai.punit.gradle

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Renders one report from the artefacts a run emitted, by running the
 * `mavai` renderer: `mavai <type> <dir> -o <file>`.
 *
 * This is the whole seam between punit and the family's renderer — a
 * report type, the directory holding the artefacts, and where the page
 * goes. Punit states nothing about the page; every figure on it is one the
 * artefact carries, and the renderer derives nothing, so the page cannot
 * disagree with what the build archived.
 *
 * The renderer walks one directory level below the root it is given, which
 * is why the verdict task hands it the parent of the XML directory and the
 * explore task runs once per service.
 */
@DisableCachingByDefault(because = "Runs an external renderer over build output; the page is the value")
abstract class MavaiReportTask : DefaultTask() {

    /** `verdict`, `explore` or `optimize`: the renderer's own command names. */
    @get:Internal
    abstract val reportType: Property<String>

    /** The directory the renderer is pointed at. */
    @get:Internal
    abstract val artefactDir: DirectoryProperty

    /** The page written, when the task renders one page. */
    @get:Internal
    abstract val outputFile: RegularFileProperty

    /**
     * When set, one page per service: the renderer runs once per child
     * directory of [artefactDir] and writes `<reportType>-<service>.html`
     * here. Explorations sit one level deeper than the other artefacts
     * (`<root>/<service>/<swept-keys>/`), so the explore report names a
     * service and its swept-keys directories become the page's groups.
     */
    @get:Internal
    abstract val perServiceOutputDir: DirectoryProperty

    /** The configuration the renderer is resolved through; see [MavaiRenderer]. */
    @get:Internal
    abstract val rendererConfiguration: Property<Configuration>

    @TaskAction
    fun render() {
        val dir = artefactDir.get().asFile
        if (!dir.isDirectory) {
            logger.lifecycle("mavai ${reportType.get()}: no artefacts under ${project.relativePath(dir)} — nothing to render.")
            return
        }
        val renderer = MavaiRenderer.locate(project, rendererConfiguration.get())
        if (renderer == null) {
            logger.lifecycle(MavaiRenderer.missingMessage())
            return
        }
        if (perServiceOutputDir.isPresent) {
            val services = dir.listFiles { f -> f.isDirectory }?.sortedBy { it.name }.orEmpty()
            if (services.isEmpty()) {
                logger.lifecycle("mavai ${reportType.get()}: no service directories under ${project.relativePath(dir)} — nothing to render.")
                return
            }
            val outputDir = perServiceOutputDir.get().asFile
            outputDir.mkdirs()
            services.forEach { service ->
                run(renderer, service, outputDir.resolve("${reportType.get()}-${service.name}.html"))
            }
            return
        }
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        run(renderer, dir, output)
    }

    private fun run(renderer: File, dir: File, output: File) {
        val command = listOf(renderer.absolutePath, reportType.get(), dir.absolutePath, "-o", output.absolutePath)
        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        val diagnostics = process.errorStream.bufferedReader().readText()
        val exit = process.waitFor()
        diagnostics.lineSequence().filter { it.isNotBlank() }.forEach { logger.info("mavai: $it") }
        if (exit != 0) {
            // The renderer exits non-zero only when nothing was renderable; its
            // stderr says which files it skipped and why.
            throw GradleException(
                "mavai ${reportType.get()} rendered nothing from ${project.relativePath(dir)} (exit $exit)" +
                    if (diagnostics.isBlank()) "" else ":\n$diagnostics"
            )
        }
        logger.lifecycle("mavai ${reportType.get()}: ${project.relativePath(output)}")
    }
}
