package org.mavai.punit.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.mavai.punit.junit5.testsubjects.PUnitSubjects;
import org.mavai.punit.internal.engine.baseline.BaselineResolver;
import org.mavai.punit.internal.reporting.VerdictSinkBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

/**
 * End-to-end emission test: drives a real typed test through the JUnit
 * platform and asserts that the XML verdict file lands on disk at the
 * configured path.
 */
@DisplayName("PUnit verdict XML emission via VerdictSinkBus")
class PUnitVerdictXmlEmissionIntegrationTest {

    private static final String JUNIT_ENGINE_ID = "junit-jupiter";
    private static final String DIR_PROPERTY = "punit.report.dir";
    private static final String BASELINE_DIR_PROPERTY = BaselineResolver.BASELINE_DIR_PROPERTY;

    @TempDir Path reportDir;
    @TempDir Path baselineDir;
    private String savedReportDir;
    private String savedBaselineDir;

    @BeforeEach
    void configurePaths() {
        savedReportDir = System.getProperty(DIR_PROPERTY);
        savedBaselineDir = System.getProperty(BASELINE_DIR_PROPERTY);
        System.setProperty(DIR_PROPERTY, reportDir.toString());
        System.setProperty(BASELINE_DIR_PROPERTY, baselineDir.toString());
        // Reset the bus so the default sink is re-installed against
        // the freshly-set report dir property.
        VerdictSinkBus.reset();
    }

    @AfterEach
    void restorePaths() {
        VerdictSinkBus.reset();
        restore(DIR_PROPERTY, savedReportDir);
        restore(BASELINE_DIR_PROPERTY, savedBaselineDir);
    }

    @Test
    @DisplayName("contractual PASS produces an XML file at {className}.{methodName}.xml")
    void contractualPassEmitsXmlFile() throws Exception {
        Events events = run(PUnitSubjects.PassingContractualTest.class);
        events.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));

        Path expected = reportDir.resolve(
                PUnitSubjects.PassingContractualTest.class.getName() + ".passes.xml");
        assertThat(expected).exists();

        String xml = Files.readString(expected);
        assertThat(xml).contains("<verdict-record")
                .contains("xmlns=\"http://mavai.org/verdict/1.0\"")
                .contains("test-name=\"passes\"")
                .contains("value=\"PASS\"");
    }

    @Test
    @DisplayName("contractual FAIL produces an XML file even when the test fails")
    void contractualFailEmitsXmlFile() throws Exception {
        Events events = run(PUnitSubjects.FailingContractualTest.class);
        events.assertStatistics(stats -> stats.started(1).succeeded(0).failed(1));

        // The XML file should be present regardless of the JUnit verdict
        // — emission happens before translate(), so a FAIL verdict also
        // lands on disk.
        try (Stream<Path> files = Files.list(reportDir)) {
            assertThat(files.count()).isGreaterThanOrEqualTo(1L);
        }
        Path expected = reportDir.resolve(
                PUnitSubjects.FailingContractualTest.class.getName() + ".fails.xml");
        assertThat(expected).exists();
        String xml = Files.readString(expected);
        assertThat(xml).contains("value=\"FAIL\"");
    }

    @Test
    @DisplayName("disabled report config (-Dpunit.report.enabled=false) suppresses emission")
    void disabledReportSuppressesEmission() throws Exception {
        String savedEnabled = System.getProperty("punit.report.enabled");
        System.setProperty("punit.report.enabled", "false");
        VerdictSinkBus.reset();
        try {
            Events events = run(PUnitSubjects.PassingContractualTest.class);
            events.assertStatistics(stats -> stats.started(1).succeeded(1));

            try (Stream<Path> files = Files.list(reportDir)) {
                assertThat(files.count()).isZero();
            }
        } finally {
            restore("punit.report.enabled", savedEnabled);
        }
    }

    private Events run(Class<?> testClass) {
        return EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(testClass))
                .execute()
                .testEvents();
    }

    private static void restore(String key, String saved) {
        if (saved == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, saved);
        }
    }
}
