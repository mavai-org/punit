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
import org.junit.jupiter.api.Assertions.assertTrue

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
                        substitute(module("org.mavai:punit-junit5")).using(project(":punit-junit5"))
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
    @DisplayName("PUnit Report Task")
    inner class PUnitReportTaskTests {

        @Test
        @DisplayName("punitReport task is registered with correct description")
        fun taskIsRegistered() {
            buildFile.writeText(buildFileWithPlugin())

            val result = runner("tasks", "--all").build()

            assertTrue(result.output.contains("punitReport - Generates an HTML report from PUnit test verdict XML files"))
        }

        @Test
        @DisplayName("punitReport task appears in verification group")
        fun taskInVerificationGroup() {
            buildFile.writeText(buildFileWithPlugin())

            val result = runner("tasks", "--group=verification").build()

            assertTrue(result.output.contains("punitReport"))
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
}
