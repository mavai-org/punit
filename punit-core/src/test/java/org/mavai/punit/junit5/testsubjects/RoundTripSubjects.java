package org.mavai.punit.junit5.testsubjects;

import static org.mavai.punit.api.criterion.Criteria.empirical;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.criterion.Criteria;
import org.mavai.punit.api.Experiment;
import org.mavai.punit.api.ProbabilisticTest;
import org.mavai.punit.api.NoFactors;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.runtime.PUnit;

/**
 * Subjects for {@code MeasureToTestRoundTripTest}.
 *
 * <p>Lives under {@code testsubjects/} so the punit-gradle-plugin's
 * {@code exclude("**}{@code /testsubjects/**")} keeps these out of the
 * normal test-discovery sweep. The hosting test sets the baseline-
 * directory system property in {@code @BeforeEach} and runs each
 * subject through {@link org.junit.platform.testkit.engine.EngineTestKit}.
 */
public final class RoundTripSubjects {

    private RoundTripSubjects() { }

    public static final String USE_CASE_ID = "round-trip-use-case";

    private static ServiceContract<NoFactors, Integer, Boolean> serviceContract() {
        return new ServiceContract<>() {
            @Override public Criteria<Boolean> criteria() {
                return empirical().passRate();
            }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
                return Outcome.ok(true);
            }
            @Override public String id() { return USE_CASE_ID; }
        };
    }

    private static Sampling<NoFactors, Integer, Boolean> sampling(int samples) {
        return Sampling.<NoFactors, Integer, Boolean>builder()
                .serviceContractFactory(f -> serviceContract())
                .inputs(1, 2, 3)
                .samples(samples)
                .build();
    }

    public static final class PassingMeasure {
        @Experiment
        void measure() {
            PUnit.measuring(sampling(50))
                    .experimentId("baseline-v1")
                    .run();
        }
    }

    public static final class EmpiricalAgainstBaseline {
        @ProbabilisticTest
        void shouldPass() {
            PUnit.testing(sampling(20))
                    .assertPasses();
        }
    }
}
