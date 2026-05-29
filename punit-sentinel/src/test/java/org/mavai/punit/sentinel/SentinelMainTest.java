package org.mavai.punit.sentinel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mavai.punit.internal.reporting.VerdictSinkBus;

/**
 * Tests for {@link SentinelMain}.
 *
 * <p>Exercises CLI argument parsing, manifest loading, and the
 * orchestration path end-to-end against the test subjects in
 * {@link org.mavai.punit.sentinel.testsubjects.SentinelTestSubjects}.
 *
 * <p>The {@link TestSystemBridge} captures the exit code instead of
 * calling {@code System.exit()} and accepts a custom classloader so
 * the test can hand SentinelMain a temp-directory manifest without
 * polluting the runtime classpath.
 */
@DisplayName("SentinelMain")
class SentinelMainTest {

    private static final String SUBJECTS_PKG =
            "org.mavai.punit.sentinel.testsubjects.SentinelTestSubjects$";

    @BeforeEach
    void resetBus() {
        VerdictSinkBus.reset();
    }

    @AfterEach
    void cleanUp() {
        VerdictSinkBus.reset();
    }

    @Nested
    @DisplayName("argument parsing")
    class ArgumentParsing {

        @Test
        @DisplayName("--help prints help and exits success")
        void helpExits() {
            TestSystemBridge system = new TestSystemBridge();
            new SentinelMain(new String[]{"--help"}, system).run();
            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_SUCCESS);
        }

        @Test
        @DisplayName("-h is a synonym for --help")
        void shortHelpExits() {
            TestSystemBridge system = new TestSystemBridge();
            new SentinelMain(new String[]{"-h"}, system).run();
            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_SUCCESS);
        }

        @Test
        @DisplayName("no command exits with usage error")
        void noCommandExitsUsage() {
            TestSystemBridge system = new TestSystemBridge();
            new SentinelMain(new String[]{}, system).run();
            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_USAGE);
        }

        @Test
        @DisplayName("unknown command exits with usage error")
        void unknownCommandExitsUsage() {
            TestSystemBridge system = new TestSystemBridge();
            new SentinelMain(new String[]{"frobnicate"}, system).run();
            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_USAGE);
        }
    }

    @Nested
    @DisplayName("manifest loading")
    class ManifestLoading {

        @Test
        @DisplayName("missing manifest exits with usage error")
        void missingManifestExitsUsage(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithEmptyClassloader(tempDir);
            new SentinelMain(new String[]{"test"}, system).run();
            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_USAGE);
        }

        @Test
        @DisplayName("manifest pointing to a missing class exits with usage error")
        void missingClassExitsUsage(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir, "no.such.class.Anywhere");
            new SentinelMain(new String[]{"test"}, system).run();
            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_USAGE);
        }
    }

    @Nested
    @DisplayName("test mode")
    class TestMode {

        @Test
        @DisplayName("passing subject yields exit-success")
        void passingSubject(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir, SUBJECTS_PKG + "PassingSubject");
            new SentinelMain(new String[]{"test"}, system).run();
            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_SUCCESS);
        }

        @Test
        @DisplayName("failing subject yields exit-failure")
        void failingSubject(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir, SUBJECTS_PKG + "FailingSubject");
            new SentinelMain(new String[]{"test"}, system).run();
            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_FAILURE);
        }

        @Test
        @DisplayName("registry containing only experiment-bearing classes yields exit-failure (no tests executed)")
        void noTestsToRunYieldsFailure(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir, SUBJECTS_PKG + "MeasuringSubject");
            new SentinelMain(new String[]{"test"}, system).run();
            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_FAILURE);
        }
    }

    @Nested
    @DisplayName("experiment mode")
    class ExperimentMode {

        @Test
        @DisplayName("'exp' command runs experiments")
        void expCommand(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir, SUBJECTS_PKG + "MeasuringSubject");
            new SentinelMain(new String[]{"exp"}, system).run();
            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_SUCCESS);
        }

        @Test
        @DisplayName("'experiment' is a synonym for 'exp'")
        void experimentIsSynonym(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir, SUBJECTS_PKG + "MeasuringSubject");
            new SentinelMain(new String[]{"experiment"}, system).run();
            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_SUCCESS);
        }
    }

    @Nested
    @DisplayName("--list")
    class ListMode {

        @Test
        @DisplayName("--list with non-empty manifest exits success")
        void listExitsSuccess(@TempDir Path tempDir) throws IOException {
            TestSystemBridge system = systemWithManifest(tempDir,
                    SUBJECTS_PKG + "PassingSubject\n" + SUBJECTS_PKG + "MeasuringSubject");
            new SentinelMain(new String[]{"--list"}, system).run();
            assertThat(system.exitCode).isEqualTo(SentinelMain.EXIT_SUCCESS);
        }
    }

    // ── Bridge / classloader plumbing ────────────────────────────────────

    private static TestSystemBridge systemWithManifest(Path dir, String content) throws IOException {
        Path metaInfDir = dir.resolve("META-INF/punit");
        Files.createDirectories(metaInfDir);
        Files.writeString(metaInfDir.resolve("sentinel-classes"), content);
        TestSystemBridge sys = new TestSystemBridge();
        sys.classLoader = classLoaderRooted(dir);
        return sys;
    }

    private static TestSystemBridge systemWithEmptyClassloader(Path dir) throws IOException {
        TestSystemBridge sys = new TestSystemBridge();
        sys.classLoader = classLoaderRooted(dir);
        return sys;
    }

    private static URLClassLoader classLoaderRooted(Path dir) throws IOException {
        URL url = dir.toUri().toURL();
        return new URLClassLoader(
                "sentinel-test",
                new URL[]{url},
                Thread.currentThread().getContextClassLoader());
    }

    private static class TestSystemBridge implements SentinelMain.SystemBridge {
        int exitCode = -1;
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        @Override
        public void exit(int code) {
            this.exitCode = code;
        }

        @Override
        public ClassLoader getClassLoader() {
            return classLoader;
        }
    }
}
