package org.mavai.punit.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import java.net.URLClassLoader
import java.nio.file.Path

/**
 * Gradle task that generates an HTML report summarising an OPTIMIZE
 * experiment's iterations.
 *
 * Uses classpath isolation via [URLClassLoader] to invoke the optimization
 * report generator from the punit-report module without a compile-time
 * dependency — the same approach as [PUnitReportTask] and
 * [PUnitExploreReportTask].
 *
 * The optimizations root is supplied as a path rather than an
 * [InputDirectory] so the task tolerates a project that has never run an
 * OPTIMIZE experiment: the generator writes a valid "no optimizations
 * found" page instead of failing. The YAML files under that root are
 * declared as optional inputs purely for up-to-date checking.
 */
@DisableCachingByDefault(because = "Report generation is cheap and its output is a human-review artefact")
abstract class PUnitOptimizeReportTask : DefaultTask() {

    @get:Input
    abstract val optimizationsRootPath: Property<String>

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val optimizationFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val htmlDir: DirectoryProperty

    @get:Classpath
    abstract val reportClasspath: ConfigurableFileCollection

    @TaskAction
    fun generateReport() {
        val rootPath = Path.of(optimizationsRootPath.get())
        val htmlPath = htmlDir.asFile.get().toPath()

        val urls = reportClasspath.files
            .filter { it.exists() }
            .map { it.toURI().toURL() }
            .toTypedArray()

        val classLoader = URLClassLoader(urls, ClassLoader.getSystemClassLoader())
        try {
            val generatorClass = classLoader.loadClass("org.mavai.punit.report.optimize.ReportGenerator")
            val generator = generatorClass.getDeclaredConstructor().newInstance()
            val generateMethod = generatorClass.getMethod("generate",
                Path::class.java, Path::class.java)
            generateMethod.invoke(generator, rootPath, htmlPath)

            logger.lifecycle("PUnit optimization comparison report generated: ${htmlPath.resolve("index.html")}")
        } finally {
            classLoader.close()
        }
    }
}
