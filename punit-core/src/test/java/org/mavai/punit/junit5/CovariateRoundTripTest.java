package org.mavai.punit.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.mavai.punit.junit5.testsubjects.CovariateSubjects;
import org.mavai.punit.internal.engine.baseline.BaselineResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

/**
 * End-to-end covariate wiring: a service contract declares a custom
 * {@code region} covariate; a measure experiment writes a baseline
 * stamped with the resolved value; an empirical test resolves the
 * matching baseline at evaluate time. Demonstrates that the
 * declaration→resolution→identity→matching loop is closed.
 */
@DisplayName("Covariate end-to-end: MEASURE writes profile, TEST resolves matching baseline")
class CovariateRoundTripTest {

    private static final String JUNIT_ENGINE_ID = "junit-jupiter";

    @TempDir Path baselineDir;
    private String savedProperty;
    private String savedCustomCovariate;

    @BeforeEach
    void setUp() {
        savedProperty = System.getProperty(BaselineResolver.BASELINE_DIR_PROPERTY);
        System.setProperty(BaselineResolver.BASELINE_DIR_PROPERTY,
                baselineDir.toString());
        savedCustomCovariate = System.getProperty(CovariateSubjects.REGION_PROPERTY);
        System.setProperty(CovariateSubjects.REGION_PROPERTY, "EU");
    }

    @AfterEach
    void tearDown() {
        restoreProperty(BaselineResolver.BASELINE_DIR_PROPERTY, savedProperty);
        restoreProperty(CovariateSubjects.REGION_PROPERTY, savedCustomCovariate);
    }

    private static void restoreProperty(String key, String saved) {
        if (saved == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, saved);
        }
    }

    @Test
    @DisplayName("measure stamps the resolved covariate profile into the baseline filename")
    void measureWritesProfileStampedFilename() throws IOException {
        Events events = run(CovariateSubjects.MeasureWithCovariate.class);
        events.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));

        try (Stream<Path> files = Files.list(baselineDir)) {
            List<Path> emitted = files.toList();
            assertThat(emitted)
                    .as("baseline emitted under %s", baselineDir)
                    .hasSize(1);
            String filename = emitted.get(0).getFileName().toString();
            // Filename ends with -{covHash}.yaml when a covariate is
            // declared. The exact hash is environment-derived; we pin
            // shape, not value.
            assertThat(filename)
                    .startsWith(CovariateSubjects.USE_CASE_ID + ".measureBaseline-")
                    // Factors fingerprint is 8 hex chars; one declared
                    // covariate adds a -{4-hex} tail.
                    .matches(".*-[0-9a-f]{8}-[0-9a-f]{4}\\.yaml$");
        }
    }

    @Test
    @DisplayName("test under matching covariate resolves the just-written baseline")
    void testResolvesMatchingBaseline() throws IOException {
        // Phase 1: write a baseline under region=EU.
        run(CovariateSubjects.MeasureWithCovariate.class)
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        // Phase 2: empirical test under same region=EU resolves it.
        Events testEvents = run(CovariateSubjects.TestWithMatchingCovariate.class);
        testEvents.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
    }

    @Test
    @DisplayName("test under non-matching covariate fails (misconfiguration: candidates exist but every one rejected)")
    void testUnderDifferentCovariateFailsAsMisconfiguration() throws IOException {
        // Phase 1: write baseline under region=EU.
        run(CovariateSubjects.MeasureWithCovariate.class)
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        // Phase 2: switch region to APAC. The EU baseline exists but
        // the framework rejects it on the CONFIGURATION-category
        // hard gate (region mismatch). Every candidate is rejected
        // and there is no empty-profile fallback baseline. The
        // typed pipeline reads this as misconfiguration — the user
        // is asking for a reference that doesn't exist for this
        // configuration — and the test FAILS, not skips.
        System.setProperty(CovariateSubjects.REGION_PROPERTY, "APAC");
        Events testEvents = run(CovariateSubjects.TestWithMatchingCovariate.class);
        testEvents.assertStatistics(stats -> stats.started(1).failed(1));
    }

    @Test
    @DisplayName("misalignment surfaces in the JUnit failure message — author sees why")
    void misalignmentExplainedInVerdict() throws IOException {
        run(CovariateSubjects.MeasureWithCovariate.class)
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        System.setProperty(CovariateSubjects.REGION_PROPERTY, "APAC");
        Events testEvents = run(CovariateSubjects.TestWithMatchingCovariate.class);

        // The failed-event payload carries the diagnostic that the
        // empirical PUnit.testing(...) translates from the test result.
        // It should mention the rejection reason for the EU baseline.
        testEvents.failed()
                .assertThatEvents()
                .anySatisfy(event -> {
                    var throwable = event.getRequiredPayload(
                            org.junit.platform.engine.TestExecutionResult.class)
                            .getThrowable().orElseThrow();
                    org.assertj.core.api.Assertions.assertThat(throwable.getMessage())
                            .contains("CONFIGURATION mismatch on region")
                            .contains("current=APAC")
                            .contains("baseline=EU");
                });
    }

    private static Events run(Class<?> testClass) {
        return EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(testClass))
                .execute()
                .testEvents();
    }
}
