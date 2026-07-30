package org.mavai.punit.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.nio.file.Path

/**
 * The `mavaiCheck` task: validates every declared contract's load-time
 * joins with zero samples — the authoring loop's compile step, runnable
 * without executing a single sample.
 *
 * Scans the test resource roots for `mavai-contract/1` files and drives
 * punit-decl's check entry over each: parse, views, selection
 * expressions, criteria, the service definition's strict configuration,
 * the per-input admission gate, the exploration grid, the declared
 * optimizations — plus the stale-artefact advisory naming exploration
 * artefacts no current grid point writes (named, never deleted). Each
 * contract's conventional bindings class (`<package>.MavaiBindings`,
 * the package derived from the contract's location under its resource
 * root) is resolved when present.
 *
 * Uses classpath isolation via [URLClassLoader] over the test runtime
 * classpath — the same approach as the report tasks — so the consumer's
 * bindings classes and service types resolve exactly as a test run
 * would see them.
 */
@DisableCachingByDefault(because = "Validation is cheap and its value is the console output")
abstract class PUnitCheckTask : DefaultTask() {

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contractFiles: ConfigurableFileCollection

    /** The test resource roots — a contract's package derives from its location under one. */
    @get:Input
    abstract val resourceRoots: ListProperty<String>

    @get:Input
    abstract val explorationsRootPath: Property<String>

    @get:Classpath
    abstract val checkClasspath: ConfigurableFileCollection

    @TaskAction
    fun check() {
        val contracts = contractFiles.files
            .filter { it.isFile && it.name.endsWith(".yaml") && isContract(it) }
            .sortedBy { it.absolutePath }
        if (contracts.isEmpty()) {
            logger.lifecycle("mavaiCheck: no mavai-contract/1 files under the test resource roots")
            return
        }
        val urls = checkClasspath.files
            .filter { it.exists() }
            .map { it.toURI().toURL() }
            .toTypedArray()
        val classLoader = URLClassLoader(urls, ClassLoader.getSystemClassLoader())
        try {
            val entry = classLoader.loadClass("org.mavai.punit.decl.ContractCheck")
            val run = entry.getMethod(
                "run", Path::class.java, String::class.java, Path::class.java)
            var failures = 0
            for (contract in contracts) {
                val bindingsClass = packageOf(contract)?.let { "$it.MavaiBindings" }
                try {
                    @Suppress("UNCHECKED_CAST")
                    val facts = run.invoke(null, contract.toPath(), bindingsClass,
                        Path.of(explorationsRootPath.get())) as List<String>
                    logger.lifecycle("OK ${contract.name}")
                    facts.forEach { logger.lifecycle("   $it") }
                } catch (refusal: InvocationTargetException) {
                    failures++
                    logger.error("REFUSED ${contract.name}: ${refusal.cause?.message}")
                }
            }
            if (failures > 0) {
                throw GradleException(
                    "mavaiCheck: $failures contract file(s) refused — see the messages above")
            }
        } finally {
            classLoader.close()
        }
    }

    private fun isContract(file: File): Boolean =
        file.useLines { lines -> lines.take(20).any { it.trim() == "format: mavai-contract/1" } }

    /** The package a contract's location under its resource root implies, or null. */
    private fun packageOf(contract: File): String? {
        for (root in resourceRoots.get()) {
            val rootFile = File(root)
            if (contract.absolutePath.startsWith(rootFile.absolutePath + File.separator)) {
                val relative = contract.parentFile.relativeTo(rootFile).path
                if (relative.isEmpty()) {
                    return null
                }
                return relative.replace(File.separatorChar, '.')
            }
        }
        return null
    }
}
