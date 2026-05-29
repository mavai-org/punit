package org.mavai.punit.junit5.testsubjects;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.mavai.punit.api.criterion.Criteria.empirical;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.criterion.Criteria;
import org.mavai.punit.api.covariate.CovariateCategory;
import org.mavai.punit.api.Experiment;
import org.mavai.punit.api.ProbabilisticTest;
import org.mavai.punit.api.NoFactors;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.covariate.Covariate;
import org.mavai.punit.runtime.PUnit;

/**
 * Test subjects for {@link org.mavai.punit.junit5.CovariateRoundTripTest}.
 *
 * <p>The service contract declares one custom covariate ({@code region}) whose
 * resolver reads a system property. The test sets the property in
 * {@code @BeforeEach}, runs a measure to stamp a baseline with the
 * resolved value, then runs an empirical test that should resolve
 * the matching baseline.
 */
public final class CovariateSubjects {

    public static final String USE_CASE_ID = "covariate-subject";
    public static final String REGION_PROPERTY = "punit.test.region";

    private CovariateSubjects() { }

    /**
     * A service contract that always passes and declares a single CONFIGURATION
     * covariate read from {@link #REGION_PROPERTY}. Hard-gating
     * CONFIGURATION means a test under {@code region=APAC} cannot
     * silently fall back to a baseline measured under {@code region=EU}.
     */
    private static ServiceContract<NoFactors, Integer, Boolean> covariateServiceContract() {
        return new ServiceContract<>() {
            @Override public Criteria<Boolean> criteria() {
                return empirical().passRate();
            }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
                return Outcome.ok(true);
            }
            @Override public String id() { return USE_CASE_ID; }

            @Override
            public List<Covariate> covariates() {
                return List.of(Covariate.custom("region",
                        CovariateCategory.CONFIGURATION));
            }

            @Override
            public Map<String, Supplier<String>> customCovariateResolvers() {
                return Map.of("region",
                        () -> System.getProperty(REGION_PROPERTY, "UNSET"));
            }
        };
    }

    private static Sampling<NoFactors, Integer, Boolean> sampling(int samples) {
        return Sampling.<NoFactors, Integer, Boolean>builder()
                .serviceContractFactory(f -> covariateServiceContract())
                .inputs(1, 2, 3)
                .samples(samples)
                .build();
    }

    /** Phase 1: stamp a baseline under whatever value REGION_PROPERTY holds. */
    public static final class MeasureWithCovariate {
        @Experiment
        void measureBaseline() {
            PUnit.measuring(sampling(100))
                    .experimentId("measureBaseline")
                    .run();
        }
    }

    /**
     * Phase 2: run the empirical test. Resolution finds the baseline
     * stamped with the matching covariate; criterion produces PASS
     * when match found, INCONCLUSIVE when no match (the
     * "different region" scenario in the round-trip test).
     */
    public static final class TestWithMatchingCovariate {
        @ProbabilisticTest
        void shouldMatchBaseline() {
            // The point of this subject is resolution-time correctness:
            // when the baseline matches we get a real verdict (not
            // INCONCLUSIVE), and when it doesn't we get INCONCLUSIVE.
            // Sample count matches the measure phase (100) so the
            // sample-size constraint holds; baseline rate is 1.0
            // (always-passes), so feasibility silently skips the
            // degenerate-rate case.
            PUnit.testing(sampling(100))
                    .assertPasses();
        }
    }
}
