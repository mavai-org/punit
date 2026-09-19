package org.mavai.punit.gradle

import java.io.File
import java.util.jar.JarFile
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

@DisplayName("PUnit Gradle Plugin")
class PUnitPluginFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private val buildFile get() = File(projectDir, "build.gradle.kts")
    private val settingsFile get() = File(projectDir, "settings.gradle.kts")

    @BeforeEach
    fun setUp() {
        settingsFile.writeText("""rootProject.name = "test-project"""")
    }

    private fun buildFileWithPlugin(extra: String = "") = """
        plugins {
            java
            id("org.mavai.punit")
        }

        repositories {
            mavenCentral()
        }

        dependencies {
            testImplementation(platform("org.junit:junit-bom:5.14.2"))
            testImplementation("org.junit.jupiter:junit-jupiter")
            testRuntimeOnly("org.junit.platform:junit-platform-launcher")
        }
        $extra
    """.trimIndent()

    private fun runner(vararg args: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments(*args)
        .forwardOutput()

    @Nested
    @DisplayName("Plugin Application")
    inner class PluginApplication {

        @Test
        @DisplayName("plugin applies without error")
        fun pluginApplies() {
            buildFile.writeText(buildFileWithPlugin())

            val result = runner("tasks", "--group=verification").build()

            assertTrue(result.output.contains("experiment"))
            assertTrue(result.output.contains("exp"))
        }

        @Test
        @DisplayName("mavaiCheck is registered and reports an empty contract scan")
        fun mavaiCheckRegistered() {
            buildFile.writeText(buildFileWithPlugin())

            val result = runner("mavaiCheck").build()

            assertTrue(result.output.contains(
                "no mavai-contract/1 files under the test resource roots"))
        }

        @Test
        @DisplayName("experiment and exp tasks are registered")
        fun experimentTasksRegistered() {
            buildFile.writeText(buildFileWithPlugin())

            val result = runner("tasks", "--all").build()

            assertTrue(result.output.contains("experiment - Runs @Experiment-tagged methods"))
            assertTrue(result.output.contains("exp - Shorthand for 'experiment' task"))
        }
    }

    @Nested
    @DisplayName("Test Task Configuration")
    inner class TestTaskConfiguration {

        @Test
        @DisplayName("test task runs successfully with plugin applied")
        fun testTaskRunsSuccessfully() {
            buildFile.writeText(buildFileWithPlugin())

            val testDir = File(projectDir, "src/test/java")
            testDir.mkdirs()
            File(testDir, "DummyTest.java").writeText("""
                import org.junit.jupiter.api.Test;
                class DummyTest {
                    @Test void works() {}
                }
            """.trimIndent())

            val result = runner("test").build()

            assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        }

        @Test
        @DisplayName("testsubject exclusion can be disabled")
        fun testSubjectExclusionCanBeDisabled() {
            buildFile.writeText(buildFileWithPlugin("""
                punit {
                    excludeTestSubjects.set(false)
                }
            """.trimIndent()))

            val result = runner("tasks", "--all").build()

            assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        }

        @Test
        @DisplayName("test task configuration can be disabled entirely")
        fun testTaskConfigurationCanBeDisabled() {
            buildFile.writeText(buildFileWithPlugin("""
                punit {
                    configureTestTask.set(false)
                }
            """.trimIndent()))

            val result = runner("tasks", "--all").build()

            // Plugin still registers experiment tasks even when test config is off
            assertTrue(result.output.contains("experiment"))
        }
    }

    @Nested
    @DisplayName("Create Sentinel Task")
    inner class CreateSentinelTask {

        @Test
        @DisplayName("createSentinel task is registered")
        fun taskIsRegistered() {
            buildFile.writeText(buildFileWithPlugin())

            val result = runner("tasks", "--group=build").build()

            assertTrue(result.output.contains("createSentinel"))
        }

        @Test
        @DisplayName("createSentinel task appears in task list with correct description")
        fun taskHasDescription() {
            buildFile.writeText(buildFileWithPlugin())

            val result = runner("tasks", "--all").build()

            assertTrue(result.output.contains("createSentinel - Builds an executable sentinel JAR"))
        }

        @Test
        @DisplayName("createSentinel produces a JAR with sentinel manifest, main class, and sentinel runtime")
        fun sentinelJarContainsRequisiteElements() {
            val punitRootDir = System.getProperty("punitRootDir")
                ?: throw IllegalStateException("punitRootDir system property not set")

            settingsFile.writeText("""
                pluginManagement {
                    includeBuild("$punitRootDir/punit-gradle-plugin")
                }
                rootProject.name = "test-project"
                includeBuild("$punitRootDir") {
                    dependencySubstitution {
                        substitute(module("org.mavai:punit-core")).using(project(":punit-core"))
                        substitute(module("org.mavai:punit-sentinel")).using(project(":punit-sentinel"))
                    }
                }
            """.trimIndent())

            buildFile.writeText("""
                plugins {
                    java
                    id("org.mavai.punit")
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    testImplementation("org.mavai:punit-core:0.0.0")
                    testImplementation(platform("org.junit:junit-bom:5.14.2"))
                    testImplementation("org.junit.jupiter:junit-jupiter")
                    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
                }
            """.trimIndent())

            val testDir = File(projectDir, "src/test/java/sentinel")
            testDir.mkdirs()
            File(testDir, "MyReliabilitySpec.java").writeText("""
                package sentinel;
                import org.mavai.punit.api.ProbabilisticTest;
                public class MyReliabilitySpec {
                    @ProbabilisticTest
                    void shoppingMeetsBaseline() {
                        // body intentionally empty — the plugin scans
                        // for the annotation, not the body
                    }
                }
            """.trimIndent())

            val result = runner("createSentinel").build()

            assertEquals(TaskOutcome.SUCCESS, result.task(":createSentinel")?.outcome)

            val sentinelJar = File(projectDir, "build/libs/test-project-sentinel.jar")
            assertTrue(sentinelJar.exists(), "Sentinel JAR should exist")

            JarFile(sentinelJar).use { jar ->
                val entryNames = jar.entries().asSequence().map { it.name }.toSet()

                // Sentinel class manifest
                assertTrue(entryNames.contains("META-INF/punit/sentinel-classes"),
                    "JAR should contain sentinel-classes manifest")
                val manifest = jar.getInputStream(jar.getEntry("META-INF/punit/sentinel-classes"))
                    .bufferedReader().readText().trim()
                assertTrue(manifest.contains("sentinel.MyReliabilitySpec"),
                    "Manifest should list the class with the typed @ProbabilisticTest method")

                // Main-Class attribute
                val mainClass = jar.manifest.mainAttributes.getValue("Main-Class")
                assertEquals("org.mavai.punit.sentinel.SentinelMain", mainClass)

                // Sentinel runtime classes
                assertTrue(entryNames.contains("org/mavai/punit/sentinel/SentinelMain.class"),
                    "JAR should contain SentinelMain")
                assertTrue(entryNames.contains("org/mavai/punit/sentinel/SentinelOrchestrator.class"),
                    "JAR should contain SentinelOrchestrator")

                // The registered class itself
                assertTrue(entryNames.contains("sentinel/MyReliabilitySpec.class"),
                    "JAR should contain the registered class")
            }
        }
    }

    @Nested
    @DisplayName("Reporting")
    inner class Reporting {

        @Test
        @DisplayName("the plugin writes no HTML itself: one punitReport task, delegating to the mavai renderer, and no per-kind writers")
        fun oneReportTaskDelegating() {
            buildFile.writeText(buildFileWithPlugin())

            val result = runner("tasks", "--all").build()

            assertTrue(result.output.contains("punitReport - Renders the verdict, exploration and optimization pages with the mavai renderer"))
            assertFalse(result.output.contains("explorationReport"))
            assertFalse(result.output.contains("optimizationReport"))
        }

        @Test
        @DisplayName("punitVerify task remains in the verification group")
        fun verifyTaskRemains() {
            buildFile.writeText(buildFileWithPlugin())

            val result = runner("tasks", "--group=verification").build()

            assertTrue(result.output.contains("punitVerify"))
        }
    }

    @Nested
    @DisplayName("Extension Customization")
    inner class ExtensionCustomization {

        @Test
        @DisplayName("custom output directories are accepted")
        fun customOutputDirs() {
            buildFile.writeText(buildFileWithPlugin("""
                punit {
                    specsDir.set("custom/specs")
                    explorationsDir.set("custom/explorations")
                    optimizationsDir.set("custom/optimizations")
                }
            """.trimIndent()))

            val result = runner("tasks", "--all").build()

            assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        }
    }

    @Nested
    @DisplayName("punitReport and the mavai renderer")
    inner class PUnitReport {

        private val isWindows = System.getProperty("os.name").lowercase().contains("windows")

        /** A stand-in renderer: a script that writes its arguments as the page (the -o path is the last argument). */
        private fun fakeRenderer(name: String = "mavai"): File {
            assumeTrue(!isWindows, "the stand-in renderer is a shell script")
            val file = File(projectDir, "tools/$name")
            file.parentFile.mkdirs()
            file.writeText("#!/bin/sh\nfor last; do :; done\nprintf '<html>%s</html>' \"$*\" > \"\$last\"\n")
            file.setExecutable(true)
            return file
        }

        private fun environmentWithout(vararg names: String): Map<String, String> =
            System.getenv().filterKeys { it !in names }

        private fun withOverride(renderer: File) =
            environmentWithout("MAVAI_BIN") + ("MAVAI_BIN" to renderer.absolutePath)

        @Test
        @DisplayName("punitReport is registered beside punitVerify")
        fun reportTaskRegistered() {
            buildFile.writeText(buildFileWithPlugin())

            val result = runner("tasks", "--group=verification").build()

            assertTrue(result.output.contains("punitReport - Renders the verdict, exploration and optimization pages"))
            assertTrue(result.output.contains("punitVerify"))
            assertFalse(result.output.contains("mavaiVerdict"))
        }

        @Test
        @DisplayName("MAVAI_BIN names the renderer; the verdict page is drawn over the parent of the XML directory")
        fun overrideRendersTheVerdictPage() {
            buildFile.writeText(buildFileWithPlugin())
            val renderer = fakeRenderer()
            File(projectDir, "build/reports/punit/xml").mkdirs()
            File(projectDir, "build/reports/punit/xml/one.xml").writeText("<verdict-record/>")

            val result = runner("punitReport", "--offline").withEnvironment(withOverride(renderer)).build()

            assertEquals(TaskOutcome.SUCCESS, result.task(":punitReport")?.outcome)
            val page = File(projectDir, "build/reports/punit/verdict.html")
            assertTrue(page.isFile, "the page is written where the task said")
            val args = page.readText()
            assertTrue(args.startsWith("<html>verdict "), "the report type leads the arguments: $args")
            assertTrue(args.contains(File(projectDir, "build/reports/punit").canonicalPath + " "), "the artefact directory is the XML directory's parent: $args")
            assertTrue(args.contains("-o ${page.canonicalPath}"), "the destination is the -o argument: $args")
            assertTrue(result.output.contains("punitReport: verdict.html under build/reports/punit (no explorations, no optimizations)"), result.output)
        }

        @Test
        @DisplayName("the renderer resolves from a Maven repository for the host platform and runs from build/mavai")
        fun resolvedRendererRuns() {
            val classifier = MavaiRenderer.classifier()
            assumeTrue(classifier != null, "the host has no published classifier")
            val version = "9.9.9"
            val repo = File(projectDir, "repo/org/mavai/mavai/$version").apply { mkdirs() }
            File(repo, "mavai-$version.pom").writeText("""
                <project><modelVersion>4.0.0</modelVersion><groupId>org.mavai</groupId>
                <artifactId>mavai</artifactId><version>$version</version><packaging>exe</packaging></project>
            """.trimIndent())
            fakeRenderer().copyTo(File(repo, "mavai-$version-$classifier.exe"))
            buildFile.writeText(buildFileWithPlugin("""
                repositories { maven { url = uri("repo") } }
                punit { mavaiVersion.set("$version") }
            """.trimIndent()))
            File(projectDir, "build/punit/optimizations/svc").mkdirs()

            val result = runner("punitReport", "--offline").withEnvironment(environmentWithout("MAVAI_BIN")).build()

            assertEquals(TaskOutcome.SUCCESS, result.task(":punitReport")?.outcome)
            val placed = File(projectDir, "build/mavai/${MavaiRenderer.executableName()}")
            assertTrue(placed.isFile && placed.canExecute(), "the resolved renderer is placed under build/mavai as an executable")
            val page = File(projectDir, "build/reports/punit/optimize.html")
            assertTrue(page.isFile && page.readText().startsWith("<html>optimize "), "the resolved renderer drew the page")
        }

        @Test
        @DisplayName("explorations render one page per service, and hideScores reaches the optimize command")
        fun exploreAndOptimizeTogether() {
            buildFile.writeText(buildFileWithPlugin("""
                tasks.named<org.mavai.punit.gradle.PUnitReportTask>("punitReport") { hideScores.set(true) }
            """.trimIndent()))
            val renderer = fakeRenderer()
            File(projectDir, "build/punit/explorations/alpha/temp").mkdirs()
            File(projectDir, "build/punit/explorations/beta/temp").mkdirs()
            File(projectDir, "build/punit/optimizations/svc").mkdirs()

            val result = runner("punitReport", "--offline").withEnvironment(withOverride(renderer)).build()

            assertEquals(TaskOutcome.SUCCESS, result.task(":punitReport")?.outcome)
            val alpha = File(projectDir, "build/reports/punit/explore-alpha.html")
            val beta = File(projectDir, "build/reports/punit/explore-beta.html")
            assertTrue(alpha.isFile && beta.isFile, "one page per service")
            assertTrue(alpha.readText().contains(File(projectDir, "build/punit/explorations/alpha").canonicalPath), "each page is drawn over its own service directory")
            val optimize = File(projectDir, "build/reports/punit/optimize.html").readText()
            assertTrue(optimize.contains(" --hide-scores -o "), "hideScores becomes the renderer's flag: $optimize")
            assertTrue(result.output.contains("punitReport: explore-alpha.html, explore-beta.html, optimize.html under build/reports/punit (no verdicts)"), result.output)
        }

        @Test
        @DisplayName("with nothing to render the task says which directories were empty")
        fun nothingToRender() {
            buildFile.writeText(buildFileWithPlugin())

            val result = runner("punitReport", "--offline").build()

            assertEquals(TaskOutcome.SUCCESS, result.task(":punitReport")?.outcome)
            assertTrue(result.output.contains("punitReport: nothing to render"), result.output)
        }

        @Test
        @DisplayName("with no renderer anywhere the task completes and says so")
        fun noRendererIsReportedNotFailed() {
            assumeTrue(!isWindows, "PATH is emptied with a POSIX layout")
            buildFile.writeText(buildFileWithPlugin("""
                punit { mavaiVersion.set("0.0.0-absent") }
            """.trimIndent()))
            File(projectDir, "build/reports/punit/xml").mkdirs()
            File(projectDir, "build/reports/punit/xml/one.xml").writeText("<verdict-record/>")
            val emptyPath = File(projectDir, "empty-path").apply { mkdirs() }

            val result = runner("punitReport", "--offline")
                .withEnvironment(environmentWithout("MAVAI_BIN", "PATH") + ("PATH" to emptyPath.absolutePath))
                .build()

            assertEquals(TaskOutcome.SUCCESS, result.task(":punitReport")?.outcome)
            assertTrue(result.output.contains("no mavai renderer for"), "the build says the page was not rendered and why")
            assertFalse(File(projectDir, "build/reports/punit/verdict.html").exists())
        }
    }
}
