package org.mavai.punit.gradle

import org.gradle.api.DefaultTask
import org.gradle.work.DisableCachingByDefault
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import java.net.URLClassLoader

/**
 * Gradle task that generates an HTML report from PUnit test verdict XML files.
 *
 * Uses classpath isolation via [URLClassLoader] to invoke [ReportGenerator]
 * from the punit-report module without a compile-time dependency.
 */
@DisableCachingByDefault(because = "Cheap validation over local files; the value is the console output")
abstract class PUnitReportTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val xmlDir: DirectoryProperty

    @get:OutputDirectory
    abstract val htmlDir: DirectoryProperty

    @get:Classpath
    abstract val reportClasspath: ConfigurableFileCollection

    @TaskAction
    fun generateReport() {
        val xmlPath = xmlDir.asFile.get().toPath()
        val htmlPath = htmlDir.asFile.get().toPath()

        val urls = reportClasspath.files
            .filter { it.exists() }
            .map { it.toURI().toURL() }
            .toTypedArray()

        val classLoader = URLClassLoader(urls, ClassLoader.getSystemClassLoader())
        try {
            val generatorClass = classLoader.loadClass("org.mavai.punit.report.ReportGenerator")
            val generator = generatorClass.getDeclaredConstructor().newInstance()
            val generateMethod = generatorClass.getMethod("generate",
                java.nio.file.Path::class.java, java.nio.file.Path::class.java)
            generateMethod.invoke(generator, xmlPath, htmlPath)

            logger.lifecycle("PUnit HTML report generated: ${htmlPath.resolve("index.html")}")
        } finally {
            classLoader.close()
        }
    }
}
