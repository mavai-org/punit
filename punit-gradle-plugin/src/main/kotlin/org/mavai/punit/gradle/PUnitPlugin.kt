package org.mavai.punit.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestOutputListener
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.io.File
import java.net.URLClassLoader

/**
 * Gradle plugin that configures PUnit probabilistic testing tasks.
 *
 * Applies to any project using PUnit:
 * - Configures the `test` task to exclude experiment-tagged methods
 *   so `./gradlew test` and CI never accidentally re-run an
 *   expensive, side-effecting experiment (e.g. an LLM-calling MEASURE
 *   that would clobber committed baseline spec files)
 * - Registers `experiment` and `exp` tasks for the inverse run —
 *   experiment-tagged methods only, with the cache/failure/output-dir
 *   defaults experiments need (uncacheable, INCONCLUSIVE-tolerant,
 *   spec/exploration/optimization output dirs wired through)
 * - Registers `createSentinel` task to build an executable sentinel JAR
 * - Forwards `punit.*` system properties and supports `-Prun=` filter syntax
 *
 * `test` and `experiment` share the same JUnit Platform engine — the
 * split between them is about runtime contract (cost, idempotence,
 * artefact production), not about distinct execution machinery.
 */
class PUnitPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("punit", PUnitExperimentExtension::class.java).apply {
            // Measure specs are inputs to subsequent @ProbabilisticTest runs
            // (including in CI), so they live alongside source and are intended
            // to be committed. Explore and Optimize outputs are human-review
            // artefacts with no downstream programmatic consumer, so they live
            // under build/ as transient build output. See CHANGELOG 0.6.0.
            specsDir.convention("src/test/resources/punit/specs")
            explorationsDir.convention(
                project.layout.buildDirectory.dir("punit/explorations").map { it.asFile.absolutePath }
            )
            optimizationsDir.convention(
                project.layout.buildDirectory.dir("punit/optimizations").map { it.asFile.absolutePath }
            )
            configureTestTask.convention(true)
            excludeTestSubjects.convention(true)
        }

        // Create a dedicated configuration for punit-sentinel so it is only
        // resolved when the createSentinel task actually runs, not on every build
        val sentinelConfig = project.configurations.create("punitSentinel") {
            isCanBeConsumed = false
            isCanBeResolved = true
        }
        val punitVersion = loadPUnitVersion()
        project.dependencies.add("punitSentinel",
            "org.mavai:punit-sentinel:$punitVersion")

        // Create a dedicated configuration for punit-report (HTML report generation)
        val reportConfig = project.configurations.create("punitReport") {
            isCanBeConsumed = false
            isCanBeResolved = true
        }
        project.dependencies.add("punitReport",
            "org.mavai:punit-report:$punitVersion")

        project.afterEvaluate {
            if (extension.configureTestTask.get()) {
                configureTestTask(project, extension)
            }

            registerExperimentTask(project, extension, "experiment",
                "Runs @Experiment-tagged methods with experiment-appropriate " +
                "defaults: uncacheable, INCONCLUSIVE-tolerant, and wired to " +
                "the spec/exploration/optimization output dirs. Excluded from " +
                "'test' so CI never accidentally re-runs them.")
            registerExperimentTask(project, extension, "exp",
                "Shorthand for 'experiment' task")

            registerCreateSentinelTask(project, extension, sentinelConfig)
            registerPUnitReportTask(project, reportConfig)
            registerExplorationReportTask(project, extension, reportConfig)
            registerPUnitVerifyTask(project, reportConfig)
        }
    }

    private fun configureTestTask(project: Project, extension: PUnitExperimentExtension) {
        project.tasks.withType(Test::class.java).named("test").configure {
            useJUnitPlatform {
                excludeTags("punit-experiment")
            }

            if (extension.excludeTestSubjects.get()) {
                exclude("**/testsubjects/**")
            }

            systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")

            // Set default report directory so VerdictXmlSink writes to where punitReport reads
            if (System.getProperty("punit.report.dir") == null) {
                systemProperty("punit.report.dir",
                    project.layout.buildDirectory.dir("reports/punit/xml").get().asFile.absolutePath)
            }

            forwardPUnitSystemProperties(this)
            applyRunFilter(project, this)
            installProgressBridge(this)
        }
    }

    private fun registerExperimentTask(
        project: Project,
        extension: PUnitExperimentExtension,
        taskName: String,
        taskDescription: String
    ) {
        project.tasks.register(taskName, Test::class.java).configure {
            description = taskDescription
            group = "verification"

            val testSourceSet = project.extensions
                .getByType(JavaPluginExtension::class.java)
                .sourceSets.getByName("test")

            testClassesDirs = testSourceSet.output.classesDirs
            classpath = testSourceSet.runtimeClasspath

            useJUnitPlatform {
                includeTags("punit-experiment")
            }

            testLogging {
                // STANDARD_OUT is dropped from `events` so per-sample
                // progress chunks (see SampleProgressBridge) aren't
                // decorated with the `STANDARD_OUT` test-name header
                // on every flush. The bridge takes responsibility for
                // relaying ALL test-stdout — progress and non-progress
                // alike — to the build's terminal, so nothing is lost.
                //
                // showStandardStreams is intentionally NOT set here:
                // setting it to `true` is a shortcut that re-adds
                // STANDARD_OUT and STANDARD_ERROR to `events`,
                // undoing the explicit removal above. Setting it to
                // `false` would also strip STANDARD_ERROR, which we
                // want to keep.
                events = setOf(
                    TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED,
                    TestLogEvent.STANDARD_ERROR
                )
                showExceptions = true
                showCauses = true
                showStackTraces = true
                exceptionFormat = TestExceptionFormat.FULL
            }
            installProgressBridge(this)

            reports {
                html.outputLocation.set(project.layout.buildDirectory.dir("reports/experiment"))
                junitXml.outputLocation.set(project.layout.buildDirectory.dir("experiment-results"))
            }

            val specsDir = extension.specsDir.get()
            val explorationsDir = extension.explorationsDir.get()
            val optimizationsDir = extension.optimizationsDir.get()

            systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
            systemProperty("punit.specs.outputDir", specsDir)
            systemProperty("punit.explorations.outputDir", explorationsDir)
            systemProperty("punit.optimizations.outputDir", optimizationsDir)

            // Deactivate @Disabled so experiments can run
            systemProperty("junit.jupiter.conditions.deactivate", "org.junit.*DisabledCondition")

            ignoreFailures = true

            // Experiments are stochastic and side-effecting (they
            // write spec/exploration/optimization YAML); Gradle's
            // up-to-date check would otherwise skip a re-run when
            // only the user's intent changed (e.g. a new --tests
            // filter or a new PUNIT_LLM_MODE).
            outputs.upToDateWhen { false }

            if (extension.excludeTestSubjects.get()) {
                exclude("**/testsubjects/**")
            }

            dependsOn("compileTestJava", "processTestResources")

            forwardPUnitSystemProperties(this)
            applyRunFilter(project, this)

            // Track start time to detect which directories received output
            var startTime = 0L
            doFirst {
                startTime = System.currentTimeMillis()
            }

            doLast {
                println("\n✓ Experiment complete.")

                val specsFile = project.file(specsDir)
                val explorationsFile = project.file(explorationsDir)
                val optimizationsFile = project.file(optimizationsDir)

                val specsUpdated = specsFile.exists() && specsFile.walkTopDown()
                    .filter { f -> f.isFile && f.lastModified() >= startTime }
                    .any()
                val explorationsUpdated = explorationsFile.exists() && explorationsFile.walkTopDown()
                    .filter { f -> f.isFile && f.lastModified() >= startTime }
                    .any()
                val optimizationsUpdated = optimizationsFile.exists() && optimizationsFile.walkTopDown()
                    .filter { f -> f.isFile && f.lastModified() >= startTime }
                    .any()

                if (specsUpdated) {
                    println("  MEASURE specs written to: $specsDir/")
                }
                if (explorationsUpdated) {
                    println("  EXPLORE results written to: $explorationsDir/")
                }
                if (optimizationsUpdated) {
                    println("  OPTIMIZE results written to: $optimizationsDir/")
                }
                if (!specsUpdated && !explorationsUpdated && !optimizationsUpdated) {
                    println("  No output files were written.")
                }
            }
        }
    }

    private fun registerCreateSentinelTask(
        project: Project,
        extension: PUnitExperimentExtension,
        sentinelConfig: org.gradle.api.artifacts.Configuration
    ) {
        project.tasks.register("createSentinel", Jar::class.java).configure {
            description = "Builds an executable sentinel JAR bundling all classes whose methods carry the typed @ProbabilisticTest or @Experiment annotation"
            group = "build"

            val testSourceSet = project.extensions
                .getByType(JavaPluginExtension::class.java)
                .sourceSets.getByName("test")
            val mainSourceSet = project.extensions
                .getByType(JavaPluginExtension::class.java)
                .sourceSets.getByName("main")

            archiveClassifier.set("sentinel")

            manifest {
                attributes(mapOf(
                    "Main-Class" to "org.mavai.punit.sentinel.SentinelMain",
                    "Implementation-Title" to "${project.name}-sentinel",
                    "Implementation-Version" to project.version
                ))
            }

            // Merge service files; first-wins for everything else
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE

            // Exclude testsubjects from the sentinel JAR if configured
            if (extension.excludeTestSubjects.get()) {
                exclude("**/testsubjects/**")
            }

            // Exclude test framework classes that are not needed at runtime
            exclude("**/archunit/**")
            exclude("META-INF/maven/**")
            exclude("META-INF/LICENSE*")
            exclude("META-INF/NOTICE*")

            dependsOn("compileTestJava", "processTestResources")
            // Ensure all upstream project JARs (including transitive) are built
            // before we resolve the classpath. Without this, transitive project
            // dependencies (e.g., app -> app-usecases -> app-tests) may be
            // missing from the fat JAR.
            dependsOn(testSourceSet.runtimeClasspath.buildDependencies)
            dependsOn(sentinelConfig.buildDependencies)

            // Because all from() calls are deferred to doFirst (to avoid
            // configuration-time classpath resolution in composite builds),
            // Gradle has no visibility of the actual inputs and cannot
            // determine staleness. Force re-execution every time.
            outputs.upToDateWhen { false }

            // All from() calls are deferred to doFirst to avoid resolving
            // testRuntimeClasspath at configuration time, which breaks
            // composite builds where included projects are not yet evaluated.
            doFirst {
                // Include compiled test and main classes
                from(testSourceSet.output)
                from(mainSourceSet.output)

                // Include all runtime dependencies, unpacking JARs
                from(testSourceSet.runtimeClasspath
                    .filter { it.exists() }
                    .map { if (it.isDirectory) it else project.zipTree(it) }
                )

                // Include punit-sentinel runtime (SentinelMain, SentinelRunner)
                from(sentinelConfig.resolve()
                    .filter { it.exists() }
                    .map { if (it.isDirectory) it else project.zipTree(it) }
                )

                val sentinelClasses = scanForSentinelClasses(testSourceSet.output.classesDirs.files,
                    testSourceSet.runtimeClasspath.files + sentinelConfig.resolve())

                if (sentinelClasses.isEmpty()) {
                    throw org.gradle.api.GradleException(
                        "No classes with typed @ProbabilisticTest or @Experiment methods found " +
                        "on the test classpath. Annotate at least one method on at least one " +
                        "class with the typed @ProbabilisticTest or @Experiment annotation.")
                }

                // Write manifest to a temp directory that gets included in the JAR
                val manifestDir = project.layout.buildDirectory
                    .dir("generated/sentinel-manifest").get().asFile
                val manifestFile = File(manifestDir, "META-INF/punit/sentinel-classes")
                manifestFile.parentFile.mkdirs()
                manifestFile.writeText(sentinelClasses.joinToString("\n") + "\n")

                from(manifestDir)

                project.logger.lifecycle("Sentinel classes discovered:")
                sentinelClasses.forEach { project.logger.lifecycle("  $it") }
            }

            doLast {
                project.logger.lifecycle(
                    "Sentinel JAR created: ${archiveFile.get().asFile.relativeTo(project.projectDir)}")
            }
        }
    }

    /**
     * Scans compiled class directories and JARs on the classpath for
     * classes whose methods carry the typed {@code @ProbabilisticTest}
     * or {@code @Experiment} annotation — those classes are what the
     * Sentinel runtime invokes, so the manifest lists them. There is no
     * class-level marker; presence of an annotated method is the
     * registration signal.
     *
     * <p>Uses an isolated {@code URLClassLoader} to avoid polluting the
     * plugin's classloader.
     *
     * @param classesDirs  test source output directories (always scanned)
     * @param classpathFiles  full runtime classpath — directories are
     *                        scanned for typed-method-bearing classes;
     *                        JARs provide classloader context and are
     *                        also scanned, since project dependencies
     *                        get packaged as JARs in composite builds
     */
    private fun scanForSentinelClasses(
        classesDirs: Set<File>,
        classpathFiles: Set<File>
    ): List<String> {
        val urls = (classesDirs + classpathFiles)
            .filter { it.exists() }
            .map { it.toURI().toURL() }
            .toTypedArray()

        val classLoader = URLClassLoader(urls, ClassLoader.getSystemClassLoader())
        val typedAnnotations = listOf(
            "org.mavai.punit.api.ProbabilisticTest",
            "org.mavai.punit.api.Experiment"
        ).map { fqn ->
            try {
                classLoader.loadClass(fqn)
            } catch (e: ClassNotFoundException) {
                throw org.gradle.api.GradleException(
                    "Could not find $fqn on classpath. Ensure punit-core is a dependency."
                )
            }
        }

        val sentinelClasses = mutableListOf<String>()

        val allClassesDirs = (classesDirs + classpathFiles)
            .filter { it.isDirectory && it.exists() }
            .distinct()

        for (classesDir in allClassesDirs) {
            scanDirectory(classesDir, classLoader, typedAnnotations, sentinelClasses)
        }

        for (jar in classpathFiles) {
            if (!jar.isFile || !jar.name.endsWith(".jar")) continue
            scanJar(jar, classLoader, typedAnnotations, sentinelClasses)
        }

        classLoader.close()
        return sentinelClasses.sorted()
    }

    private fun scanDirectory(
        classesDir: File,
        classLoader: URLClassLoader,
        typedAnnotations: List<Class<*>>,
        results: MutableList<String>
    ) {
        classesDir.walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .filter { !it.path.contains("testsubjects") }
            .forEach { classFile ->
                val className = classFile.relativeTo(classesDir).path
                    .removeSuffix(".class")
                    .replace(File.separatorChar, '.')
                checkForSentinel(className, classLoader, typedAnnotations, results)
            }
    }

    private fun scanJar(
        jar: File,
        classLoader: URLClassLoader,
        typedAnnotations: List<Class<*>>,
        results: MutableList<String>
    ) {
        try {
            java.util.jar.JarFile(jar).use { jarFile ->
                jarFile.entries().asSequence()
                    .filter { it.name.endsWith(".class") }
                    .filter { !it.name.contains("testsubjects") }
                    .forEach { entry ->
                        val className = entry.name
                            .removeSuffix(".class")
                            .replace('/', '.')
                        checkForSentinel(className, classLoader, typedAnnotations, results)
                    }
            }
        } catch (_: Exception) {
            // Skip JARs that can't be read
        }
    }

    private fun checkForSentinel(
        className: String,
        classLoader: URLClassLoader,
        typedAnnotations: List<Class<*>>,
        results: MutableList<String>
    ) {
        try {
            val clazz = classLoader.loadClass(className)
            val hasTypedMethod = clazz.declaredMethods.any { method ->
                method.annotations.any { annotation ->
                    typedAnnotations.any { it.isInstance(annotation) }
                }
            }
            if (hasTypedMethod) {
                results.add(className)
            }
        } catch (_: Throwable) {
            // Skip classes that can't be loaded
        }
    }

    private fun registerPUnitReportTask(
        project: Project,
        reportConfig: org.gradle.api.artifacts.Configuration
    ) {
        project.tasks.register("punitReport", PUnitReportTask::class.java).configure {
            description = "Generates an HTML report from PUnit test verdict XML files"
            group = "verification"
            xmlDir.set(project.layout.buildDirectory.dir("reports/punit/xml"))
            htmlDir.set(project.layout.buildDirectory.dir("reports/punit/html"))
            reportClasspath.from(reportConfig)
        }
    }

    private fun registerExplorationReportTask(
        project: Project,
        extension: PUnitExperimentExtension,
        reportConfig: org.gradle.api.artifacts.Configuration
    ) {
        project.tasks.register("explorationReport", PUnitExploreReportTask::class.java).configure {
            description = "Generates an HTML report comparing EXPLORE experiment variants"
            group = "verification"
            val rootPath = extension.explorationsDir
            explorationsRootPath.set(rootPath)
            // Optional input-file tracking only; a missing root resolves to an
            // empty tree, so the task still runs and emits a "no explorations" page.
            explorationFiles.from(rootPath.map { project.fileTree(it) { include("**/*.yaml") } })
            htmlDir.set(project.layout.buildDirectory.dir("reports/punit-explorations/html"))
            reportClasspath.from(reportConfig)
        }
    }

    private fun registerPUnitVerifyTask(
        project: Project,
        reportConfig: org.gradle.api.artifacts.Configuration
    ) {
        val verifyTask = project.tasks.register("punitVerify", PUnitVerifyTask::class.java) {
            description = "Verifies that all probabilistic test verdicts passed"
            group = "verification"
            xmlDir.set(project.layout.buildDirectory.dir("reports/punit/xml"))
            reportClasspath.from(reportConfig)

            // Run after tests so verdict XMLs are available
            mustRunAfter(project.tasks.named("test"))
        }

        // Wire into the check lifecycle
        project.tasks.named("check").configure {
            dependsOn(verifyTask)
        }
    }

    private fun loadPUnitVersion(): String = PUnitVersion.VERSION

    private fun forwardPUnitSystemProperties(task: Test) {
        System.getProperties()
            .filter { (k, _) -> k.toString().startsWith("punit.") }
            .forEach { (k, v) -> task.systemProperty(k.toString(), v.toString()) }
    }

    private fun applyRunFilter(project: Project, task: Test) {
        val runFilter = project.findProperty("run") as String?
        if (runFilter != null) {
            if (runFilter.contains(".")) {
                task.filter.includeTestsMatching("*$runFilter")
            } else {
                task.filter.includeTestsMatching("*$runFilter*")
            }
        }
    }

    /**
     * Wire per-sample progress emission from the test JVM through to
     * the build's terminal as in-place updates.
     *
     * Punit's [SerialSampleExecutor] writes a marker-prefixed line
     * after every sample. Inside the test JVM that line goes to
     * stdout, which Gradle captures over a pipe — by default, Gradle
     * decorates the chunk with a `STANDARD_OUT` test-name header on
     * every flush, which is fine for ad-hoc test stdout but ruins the
     * per-sample progress idea (verbose vertical scroll, double
     * lines around carriage returns, the user complains rightly).
     *
     * Approach:
     *
     *   1. Drop `STANDARD_OUT` from `testLogging.events` so Gradle
     *      stops decorating test-stdout chunks. (We re-emit non-progress
     *      stdout from inside the listener, so nothing is silently lost.)
     *   2. Register a `TestOutputListener` that pattern-matches the
     *      marker, strips it, and writes a `\r`-prefixed counter to the
     *      build process's own stdout. The build's stdout is a real
     *      terminal handle (not a pipe), so the carriage return
     *      collapses each emission onto a single updating line.
     *   3. Non-progress chunks are passed through unmodified — same
     *      visual effect as the dropped `STANDARD_OUT` event would have
     *      produced, minus the per-line decoration.
     *
     * The marker constant must stay byte-identical with
     * `SerialSampleExecutor.SAMPLE_PROGRESS_MARKER`. The two live
     * in different Gradle projects with separate classpaths — this
     * code runs in the build process, the executor runs in the test
     * JVM — so a shared Java/Kotlin import is impractical.
     */
    private fun installProgressBridge(task: Test) {
        task.addTestOutputListener(SampleProgressBridge())
    }

    private companion object {
        /**
         * Mirrors `SerialSampleExecutor.SAMPLE_PROGRESS_MARKER` in
         * punit-core. Cross-module invariant: a change to one is a
         * change to both.
         */
        const val SAMPLE_PROGRESS_MARKER = "[PUNIT-PROGRESS]"
    }

    /**
     * Receives every chunk of test JVM stdout/stderr. Recognises
     * progress lines by their marker prefix, strips the marker, and
     * writes the counter to the build's terminal as an in-place
     * `\r`-prefixed update. Non-progress chunks pass through
     * unmodified.
     *
     * Made package-private (default Kotlin visibility) so the
     * functional-test module can verify the bridge end-to-end.
     */
    internal class SampleProgressBridge : TestOutputListener {

        override fun onOutput(testDescriptor: TestDescriptor, outputEvent: TestOutputEvent) {
            val message = outputEvent.message
            // The marker may appear at any byte offset in the chunk —
            // Gradle sometimes batches a leading newline with the
            // following line into one event. Find the marker rather
            // than insisting it sit at column 0.
            val markerIdx = message.indexOf(SAMPLE_PROGRESS_MARKER)
            if (markerIdx < 0) {
                System.out.print(message)
                System.out.flush()
                return
            }
            // Anything before the marker is non-progress content from
            // the same chunk — pass it through verbatim.
            if (markerIdx > 0) {
                System.out.print(message.substring(0, markerIdx))
            }
            // Extract the counter content: everything between the
            // marker and the next newline (the executor terminates
            // each emission with a newline so chunks line-align).
            val afterMarker = markerIdx + SAMPLE_PROGRESS_MARKER.length
            val newlineIdx = message.indexOf('\n', afterMarker)
            val end = if (newlineIdx < 0) message.length else newlineIdx
            val counter = message.substring(afterMarker, end)
            System.out.print('\r' + counter)
            System.out.flush()
            // If the chunk carried trailing content beyond the line
            // (rare), pass it through too.
            if (newlineIdx >= 0 && newlineIdx + 1 < message.length) {
                System.out.print(message.substring(newlineIdx + 1))
                System.out.flush()
            }
        }
    }
}
