package org.mavai.punit.junit5;

import java.io.IOException;
import java.nio.file.Path;

import org.mavai.punit.junit5.testsubjects.CovariateSubjects;
import org.mavai.punit.internal.engine.baseline.BaselineResolver;
import org.mavai.punit.runtime.PUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

/**
 * Pins the covariate-misalignment trichotomy that
 * {@link PUnit#translate} encodes:
 *
 * <ul>
 *   <li><b>Case 1: legitimate INCONCLUSIVE.</b> No candidate
 *       baseline files exist at all (the workflow is "measure first,
 *       then test"). The author hasn't yet seeded a baseline. The
 *       test produces {@code INCONCLUSIVE}, which the framework
 *       maps to JUnit-aborted (skipped).</li>
 *
 *   <li><b>Case 2: misconfiguration FAIL.</b> Candidate baselines
 *       exist but every one is rejected — typically because their
 *       covariate profiles disagree with the current run on a
 *       CONFIGURATION-category covariate (hard gate). The user is
 *       asking for a reference that doesn't exist for this
 *       configuration. The framework reads this as misconfiguration
 *       and the test FAILS (JUnit-failed).</li>
 *
 *   <li><b>Case 3: matched and verdict-proceeds.</b> A candidate
 *       matched (possibly partially); the verdict comes from the
 *       criterion's own evaluation. Misalignment notes from soft
 *       categories surface as warnings on the verdict.
 *       {@link CovariateRoundTripTest} pins this end-to-end.</li>
 * </ul>
 */
@DisplayName("Covariate misalignment trichotomy — INCONCLUSIVE vs FAIL")
class CovariateMisalignmentTrichotomyTest {

    private static final String JUNIT_ENGINE_ID = "junit-jupiter";

    @TempDir Path baselineDir;
    private String savedBaselineProperty;
    private String savedRegionProperty;

    @BeforeEach
    void setUp() {
        savedBaselineProperty = System.getProperty(BaselineResolver.BASELINE_DIR_PROPERTY);
        System.setProperty(BaselineResolver.BASELINE_DIR_PROPERTY, baselineDir.toString());
        savedRegionProperty = System.getProperty(CovariateSubjects.REGION_PROPERTY);
        System.setProperty(CovariateSubjects.REGION_PROPERTY, "EU");
    }

    @AfterEach
    void tearDown() {
        restore(BaselineResolver.BASELINE_DIR_PROPERTY, savedBaselineProperty);
        restore(CovariateSubjects.REGION_PROPERTY, savedRegionProperty);
    }

    @Test
    @DisplayName("Case 1: no candidates at all — JUnit aborted (legitimate INCONCLUSIVE)")
    void case1_noCandidatesAborts() {
        // Skip the measure phase deliberately. No baseline files
        // exist anywhere; the BaselineSelector has nothing to
        // consider. INCONCLUSIVE is legitimate — the author needs
        // to run the measure experiment first.
        Events testEvents = run(CovariateSubjects.TestWithMatchingCovariate.class);
        testEvents.assertStatistics(stats -> stats.started(1).aborted(1).failed(0));
    }

    @Test
    @DisplayName("Case 2: candidates exist but all rejected — JUnit failed (misconfiguration)")
    void case2_allRejectedFails() throws IOException {
        // Phase 1: seed a baseline under region=EU.
        run(CovariateSubjects.MeasureWithCovariate.class)
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        // Phase 2: switch to region=APAC. The EU baseline exists
        // but is CONFIG-rejected on the region mismatch. Every
        // candidate is rejected; no empty-profile fallback exists.
        // Misconfiguration → FAIL.
        System.setProperty(CovariateSubjects.REGION_PROPERTY, "APAC");
        Events testEvents = run(CovariateSubjects.TestWithMatchingCovariate.class);
        testEvents.assertStatistics(stats -> stats.started(1).aborted(0).failed(1));
    }

    @Test
    @DisplayName("Case 3: candidate matched — verdict proceeds (PASS in this scenario)")
    void case3_matchedProceedsToVerdict() throws IOException {
        // Phase 1: seed a baseline under region=EU.
        run(CovariateSubjects.MeasureWithCovariate.class)
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        // Phase 2: stay under region=EU. Baseline matches; the
        // criterion's own evaluation produces PASS.
        Events testEvents = run(CovariateSubjects.TestWithMatchingCovariate.class);
        testEvents.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
    }

    private static void restore(String key, String saved) {
        if (saved == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, saved);
        }
    }

    private static Events run(Class<?> testClass) {
        return EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(testClass))
                .execute()
                .testEvents();
    }
}
