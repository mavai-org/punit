package org.mavai.punit.gradle

import org.gradle.api.DefaultTask
import org.gradle.work.DisableCachingByDefault
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import java.net.URLClassLoader

/**
 * Gradle task that reads PUnit verdict XML files and fails the build
 * if any probabilistic test verdict is FAIL.
 *
 * This is the CI enforcement point for probabilistic tests. It mirrors
 * the JaCoCo pattern: test execution produces data, this verification
 * task interprets that data against policy.
 *
 * Wired into the `check` lifecycle so `./gradlew check` catches
 * verdict failures even when individual sample failures are suppressed.
 */
@DisableCachingByDefault(because = "Cheap validation over local files; the value is the console output")
abstract class PUnitVerifyTask : DefaultTask() {

    @get:Internal
    abstract val xmlDir: DirectoryProperty

    @get:Classpath
    abstract val reportClasspath: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val xmlPath = xmlDir.asFile.orNull?.toPath()
        if (xmlPath == null || !java.nio.file.Files.isDirectory(xmlPath)) {
            logger.lifecycle("PUnit verify: no verdict XML directory found — skipping.")
            return
        }

        val xmlCount = java.nio.file.Files.list(xmlPath).use { files ->
            files.filter { it.toString().endsWith(".xml") }.count()
        }
        if (xmlCount == 0L) {
            logger.lifecycle("PUnit verify: no verdict XML files found — skipping.")
            return
        }

        val urls = reportClasspath.files
            .filter { it.exists() }
            .map { it.toURI().toURL() }
            .toTypedArray()

        val classLoader = URLClassLoader(urls, ClassLoader.getSystemClassLoader())
        try {
            val verifierClass = classLoader.loadClass("org.mavai.punit.report.VerdictVerifier")
            val verifier = verifierClass.getDeclaredConstructor().newInstance()

            val verifyMethod = verifierClass.getMethod("verify", java.nio.file.Path::class.java)
            val result = verifyMethod.invoke(verifier, xmlPath)

            val passedMethod = result.javaClass.getMethod("passed")
            val passed = passedMethod.invoke(result) as Boolean

            val totalMethod = result.javaClass.getMethod("total")
            val total = totalMethod.invoke(result) as Int

            if (passed) {
                logger.lifecycle("PUnit verify: all $total probabilistic test(s) passed.")
            } else {
                val formatMethod = verifierClass.getMethod("formatFailureMessage", result.javaClass)
                val message = formatMethod.invoke(verifier, result) as String
                throw GradleException(message)
            }
        } finally {
            classLoader.close()
        }
    }
}
