package org.mavai.punit.junit5.testsubjects;

import java.util.concurrent.atomic.AtomicInteger;

import static org.mavai.punit.api.criterion.Criteria.empirical;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.Experiment;
import org.mavai.punit.api.ProbabilisticTest;
import org.mavai.punit.api.criterion.Criteria;
import org.mavai.punit.api.NoFactors;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.runtime.PUnit;

/**
 * Test subjects for the baseline-existence preflight short-circuit.
 *
 * <p>The service contract carries a static invoke counter. Tests that exercise
 * the short-circuit assert the counter ends at zero — the framework
 * must not have driven the engine when the verdict was structurally
 * guaranteed to be INCONCLUSIVE-no-baseline.
 */
public final class PreflightSubjects {

    public static final String USE_CASE_ID = "preflight-subject";
    public static final AtomicInteger INVOKE_COUNT = new AtomicInteger();

    private PreflightSubjects() { }

    /** Always-passing service contract that records every invocation. */
    private static ServiceContract<NoFactors, Integer, Boolean> countingServiceContract() {
        return new ServiceContract<>() {
            @Override public Criteria<Boolean> criteria() {
                return empirical().passRate();
            }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
                INVOKE_COUNT.incrementAndGet();
                return Outcome.ok(true);
            }
            @Override public String id() { return USE_CASE_ID; }
        };
    }

    private static Sampling<NoFactors, Integer, Boolean> sampling(int samples) {
        return Sampling.<NoFactors, Integer, Boolean>builder()
                .serviceContractFactory(f -> countingServiceContract())
                .inputs(1, 2, 3)
                .samples(samples)
                .build();
    }

    /**
     * Empirical test against a baseline directory that holds nothing for
     * this service contract → preflight short-circuit, engine never runs, JUnit
     * aborts.
     */
    public static final class EmpiricalNoBaselineTest {
        @ProbabilisticTest
        void empiricalWithoutBaseline() {
            PUnit.testing(sampling(20))
                    .assertPasses();
        }
    }

    /**
     * Phase 1 of the resolved-baseline regression: stamp a baseline so
     * the paired test below has something to resolve.
     */
    public static final class MeasureBaseline {
        @Experiment
        void measure() {
            PUnit.measuring(sampling(100)).run();
        }
    }

    /**
     * Phase 2 of the resolved-baseline regression: empirical test against
     * a baseline that <em>does</em> exist → preflight does not
     * short-circuit, engine runs.
     */
    public static final class EmpiricalWithBaselineTest {
        @ProbabilisticTest
        void empiricalWithBaseline() {
            // Sample count matches the measure phase (100) so the
            // sample-size constraint holds; baseline rate is 1.0
            // (always-passes), which silently skips feasibility's
            // degenerate-rate case. No .atConfidence(...) override —
            // the framework default sits comfortably above the
            // soundness floor.
            PUnit.testing(sampling(100))
                    .assertPasses();
        }
    }
}
