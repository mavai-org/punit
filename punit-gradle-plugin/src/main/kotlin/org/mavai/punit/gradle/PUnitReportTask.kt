package org.mavai.punit.gradle

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Renders the reports for whatever a run left behind, by running the
 * `mavai` renderer once per report: `mavai <type> <dir> -o <file>`.
 *
 * One task, because the developer wants the pages, not a choice between
 * artefact kinds: after `./gradlew test` it draws the verdict page; after
 * `./gradlew exp` the exploration page for each service and the
 * optimization page; whichever directories are absent are named as skipped
 * on one summary line. It is the Gradle spelling of the family's `report`
 * verb (`basel report` in baseltest), and it pairs with `punitVerify`:
 * verify gates the build on the verdicts, report draws them. It is not
 * wired into `check` — rendering is not verification.
 *
 * The task is the whole seam between punit and the renderer: a report
 * type, the directory holding the artefacts, and where the page goes. Punit
 * states nothing about the page; every figure on it is one the artefact
 * carries, and the renderer derives nothing, so the page cannot disagree
 * with what the build archived.
 *
 * The renderer walks one directory level below the root it is given, which
 * is why the verdict page is drawn over the parent of the XML directory and
 * the exploration page is drawn once per service (explorations sit one
 * level deeper: `<root>/<service>/<swept-keys>/`).
 */
@DisableCachingByDefault(because = "Runs an external renderer over build output; the pages are the value")
abstract class PUnitReportTask : DefaultTask() {

    /** Where `VerdictXmlSink` writes; the verdict page is drawn over its parent. */
    @get:Internal
    abstract val verdictXmlDir: DirectoryProperty

    /** The `punit { explorationsDir }` root: one exploration page per service directory beneath it. */
    @get:Internal
    abstract val explorationsDir: DirectoryProperty

    /** The `punit { optimizationsDir }` root: one optimization page over it. */
    @get:Internal
    abstract val optimizationsDir: DirectoryProperty

    /** Where the pages go: `verdict.html`, `explore-<service>.html`, `optimize.html`. */
    @get:Internal
    abstract val outputDir: DirectoryProperty

    /**
     * Omit the score displays on the optimization page (the ranking is
     * unchanged) — the renderer's `--hide-scores`, for runs whose scorer is
     * the observed pass rate. Default: `false`.
     */
    @get:Internal
    abstract val hideScores: Property<Boolean>

    /** The configuration the renderer is resolved through; see [MavaiRenderer]. */
    @get:Internal
    abstract val rendererConfiguration: Property<Configuration>

    @TaskAction
    fun render() {
        val verdictXml = verdictXmlDir.get().asFile
        val explorations = explorationsDir.get().asFile
        val optimizations = optimizationsDir.get().asFile

        val hasVerdicts = verdictXml.isDirectory && verdictXml.listFiles { f -> f.isFile && f.name.endsWith(".xml") }.orEmpty().isNotEmpty()
        val services = if (explorations.isDirectory) explorations.listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name } else emptyList()
        val hasOptimizations = optimizations.isDirectory && optimizations.listFiles { f -> f.isDirectory }.orEmpty().isNotEmpty()

        if (!hasVerdicts && services.isEmpty() && !hasOptimizations) {
            logger.lifecycle("punitReport: nothing to render — no verdict XML under ${project.relativePath(verdictXml)}, no explorations under ${project.relativePath(explorations)}, no optimizations under ${project.relativePath(optimizations)}. Run test or exp first.")
            return
        }

        val renderer = MavaiRenderer.locate(project, rendererConfiguration.get())
        if (renderer == null) {
            logger.lifecycle("punitReport: " + MavaiRenderer.missingMessage())
            return
        }

        val output = outputDir.get().asFile
        output.mkdirs()
        val drawn = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        if (hasVerdicts) {
            drawn += run(renderer, "verdict", verdictXml.parentFile, output.resolve("verdict.html"))
        } else {
            skipped += "no verdicts"
        }
        if (services.isNotEmpty()) {
            services.forEach { service ->
                drawn += run(renderer, "explore", service, output.resolve("explore-${service.name}.html"))
            }
        } else {
            skipped += "no explorations"
        }
        if (hasOptimizations) {
            val flags = if (hideScores.getOrElse(false)) listOf("--hide-scores") else emptyList()
            drawn += run(renderer, "optimize", optimizations, output.resolve("optimize.html"), flags)
        } else {
            skipped += "no optimizations"
        }

        val where = project.relativePath(output)
        logger.lifecycle("punitReport: ${drawn.joinToString(", ")} under $where" +
            if (skipped.isEmpty()) "" else " (${skipped.joinToString(", ")})")
    }

    /** Runs one report; returns the page's file name for the summary. */
    private fun run(renderer: File, type: String, dir: File, output: File, flags: List<String> = emptyList()): String {
        val command = listOf(renderer.absolutePath, type, dir.absolutePath) + flags + listOf("-o", output.absolutePath)
        val process = ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        val diagnostics = process.errorStream.bufferedReader().readText()
        val exit = process.waitFor()
        diagnostics.lineSequence().filter { it.isNotBlank() }.forEach { logger.info("mavai $type: $it") }
        if (exit != 0) {
            // The renderer exits non-zero only when nothing was renderable; its
            // stderr says which files it skipped and why.
            throw GradleException(
                "mavai $type rendered nothing from ${project.relativePath(dir)} (exit $exit)" +
                    if (diagnostics.isBlank()) "" else ":\n$diagnostics"
            )
        }
        return output.name
    }
}
