package org.mavai.punit.junit5.testsubjects;

import java.util.concurrent.atomic.AtomicInteger;

import static org.mavai.punit.api.criterion.Criteria.empirical;
import static org.mavai.punit.api.criterion.Criteria.meeting;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.criterion.Criteria;
import org.mavai.punit.api.ProbabilisticTest;
import org.mavai.punit.api.NoFactors;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.internal.engine.baseline.PowerAnalysis;
import org.mavai.punit.runtime.PUnit;

/**
 * Test subjects for {@code PreflightInvariantsTest}, which audits the
 * pre-flight invariants the framework upholds end-to-end through the
 * typed authoring surface — declared-threshold feasibility,
 * declared-sample-size feasibility, power-analysis-derived feasibility,
 * parameter validation, and configuration coherence. The hosting test
 * counts samples actually executed via {@link #INVOKE_COUNT} so the
 * abort-before-sampling guarantee can be asserted directly.
 *
 * <p>The soundness floor (≥ 80% confidence regardless of intent) is
 * intentionally not exercised here — the audit recorded it as a gap
 * tracked in a separate orchestrator directive.
 */
public final class PreflightInvariantSubjects {

    public static final String USE_CASE_ID = "preflight-invariant-subject";
    public static final AtomicInteger INVOKE_COUNT = new AtomicInteger();

    private PreflightInvariantSubjects() { }

    private static ServiceContract<NoFactors, Integer, Boolean> countingEmpirical() {
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

    private static ServiceContract<NoFactors, Integer, Boolean> countingContractualHighThreshold() {
        return new ServiceContract<>() {
            @Override public Criteria<Boolean> criteria() {
                return meeting().passRate(0.9999);
            }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
                INVOKE_COUNT.incrementAndGet();
                return Outcome.ok(true);
            }
            @Override public String id() { return USE_CASE_ID; }
        };
    }

    private static Sampling<NoFactors, Integer, Boolean> empiricalSampling(int samples) {
        return Sampling.<NoFactors, Integer, Boolean>builder()
                .serviceContractFactory(f -> countingEmpirical())
                .inputs(1, 2, 3)
                .samples(samples)
                .build();
    }

    private static Sampling<NoFactors, Integer, Boolean> contractualHighSampling(int samples) {
        return Sampling.<NoFactors, Integer, Boolean>builder()
                .serviceContractFactory(f -> countingContractualHighThreshold())
                .inputs(1, 2, 3)
                .samples(samples)
                .build();
    }

    /**
     * Declared (samples, threshold) pair under a normative origin.
     * The configured sample size is too small to underwrite the
     * declared threshold at the default confidence; the pre-flight
     * gate must abort before the engine runs any samples.
     */
    public static final class DeclaredThresholdInfeasibleTest {
        @ProbabilisticTest
        void undersizedAgainstNormativeThreshold() {
            // No count of 50 samples can demonstrate 0.9999 at alpha 0.05.
            PUnit.testing(contractualHighSampling(50))
                    .assertPasses();
        }
    }

    /**
     * An empirical test of 10 samples; its paired test writes a baseline
     * of 5, so the test is larger than its baseline and is refused before
     * sampling.
     */
    public static final class DeclaredSampleSizeInfeasibleTest {
        @ProbabilisticTest
        void undersizedAgainstHighBaselineRate() {
            // Larger than the baseline it consumes → refused.
            PUnit.testing(empiricalSampling(10))
                    .assertPasses();
        }
    }

    /**
     * Power-analysis-derived sample size against a real baseline.
     * The framework returns a sample count sized for the requested
     * (mde, power) tuple; using that count configures the test to be
     * feasible by construction. The pre-flight gate does not fire and
     * the engine runs.
     */
    public static final class PowerAnalysisDerivedFeasibleTest {

        public static final String EXPERIMENT_ID = "power-analysis-baseline";

        private org.mavai.punit.api.spec.Experiment baseline() {
            return PUnit.measuring(empiricalSampling(200))
                    .experimentId(EXPERIMENT_ID)
                    .build();
        }

        @ProbabilisticTest
        void powerAnalysisSampleSizeIsFeasible() {
            // PowerAnalysis derives n from (baseline rate, mde, power);
            // the test then runs with that n. By construction the
            // (n, baseline-rate, default-confidence) tuple is feasible.
            int n = PowerAnalysis.sampleSize(this::baseline, 0.05, 0.80);
            PUnit.testing(empiricalSampling(n))
                    .assertPasses();
        }
    }

    /**
     * Companion @Experiment for the power-analysis baseline. The
     * hosting test runs this first to seed the baseline, then runs
     * {@link PowerAnalysisDerivedFeasibleTest} which consumes it.
     */
    public static final class PowerAnalysisBaselineMeasure {
        @org.mavai.punit.api.Experiment
        void seedBaseline() {
            PUnit.measuring(empiricalSampling(200))
                    .experimentId(PowerAnalysisDerivedFeasibleTest.EXPERIMENT_ID)
                    .run();
        }
    }

    /**
     * Parameter validation at construction time. The framework
     * rejects out-of-range parameter values with
     * {@link IllegalArgumentException} before any pre-flight gate
     * runs. This subject demonstrates the catch in a TestKit-driven
     * frame: the {@code @ProbabilisticTest} method itself throws on
     * invocation.
     */
}
