package org.mavai.punit.junit5.testsubjects;

import static org.mavai.punit.api.criterion.Criteria.empirical;
import static org.mavai.punit.api.criterion.Criteria.meeting;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.criterion.Criteria;
import org.mavai.punit.api.ProbabilisticTest;
import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.NoFactors;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.runtime.PUnit;

/**
 * Subjects for {@code FeasibilityIntegrationTest}. The hosting test
 * pre-populates the configured baseline directory with a hand-written
 * baseline at a known rate, then runs each subject through TestKit.
 *
 * <p>Lives under {@code testsubjects/} so the punit-gradle-plugin's
 * exclude keeps these out of the normal test-discovery sweep.
 */
public final class FeasibilitySubjects {

    private FeasibilitySubjects() { }

    public static final String USE_CASE_ID = "feasibility-use-case";

    private static ServiceContract<NoFactors, Integer, Boolean> alwaysPassesEmpirical() {
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

    private static ServiceContract<NoFactors, Integer, Boolean> alwaysPassesContractual() {
        return new ServiceContract<>() {
            @Override public Criteria<Boolean> criteria() {
                return meeting().passRate(0.9999);
            }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
                return Outcome.ok(true);
            }
            @Override public String id() { return USE_CASE_ID; }
        };
    }

    private static Sampling<NoFactors, Integer, Boolean> empiricalSampling(int samples) {
        return Sampling.<NoFactors, Integer, Boolean>builder()
                .serviceContractFactory(f -> alwaysPassesEmpirical())
                .inputs(1, 2, 3)
                .samples(samples)
                .build();
    }

    private static Sampling<NoFactors, Integer, Boolean> contractualSampling(int samples) {
        return Sampling.<NoFactors, Integer, Boolean>builder()
                .serviceContractFactory(f -> alwaysPassesContractual())
                .inputs(1, 2, 3)
                .samples(samples)
                .build();
    }

    /**
     * VERIFICATION + adequately sized sample: feasibility check passes,
     * engine runs, criterion produces PASS.
     */
    public static final class VerificationFeasible {
        @ProbabilisticTest
        void shouldPass() {
            // n=50 against a baseline of 100: a valid design.
            PUnit.testing(empiricalSampling(50))
                    .assertPasses();
        }
    }

    /**
     * A test of 10 samples; its paired test writes a baseline of 5, so
     * the configuration is refused before the engine runs
     * (TEST_LARGER_THAN_BASELINE).
     */
    public static final class VerificationInfeasible {
        @ProbabilisticTest
        void shouldFailFast() {
            // Refused whatever the intent: the baseline is smaller than the test.
            PUnit.testing(empiricalSampling(10))
                    .assertPasses();
        }
    }

    /**
     * SMOKE, a small empirical test: the engine runs and produces a
     * verdict.
     */
    public static final class SmokeInfeasible {
        @ProbabilisticTest
        void shouldRunSilently() {
            PUnit.testing(empiricalSampling(10))
                    .intent(TestIntent.SMOKE)
                    .assertPasses();
        }
    }

    /**
     * VERIFICATION + contractual threshold + undersized sample:
     * a declared SLA / SLO / POLICY threshold is no less in need
     * of statistical underwriting than an empirical one. No count of 50
     * samples can demonstrate 99.99% at alpha 0.05 (the minimum is
     * 29,956); under the default VERIFICATION intent the configuration is
     * refused (COMPLIANCE_INFEASIBLE)
     * before the engine runs any samples.
     */
    public static final class ContractualVerificationInfeasible {
        @ProbabilisticTest
        void shouldFailFast() {
            PUnit.testing(contractualSampling(50))
                    .assertPasses();
        }
    }

    /**
     * SMOKE + contractual threshold + undersized sample: same
     * configuration as {@link ContractualVerificationInfeasible}
     * but with explicit SMOKE intent. The framework is silent and
     * the engine runs to a real verdict.
     */
    public static final class ContractualSmokeInfeasible {
        @ProbabilisticTest
        void shouldRunSilently() {
            PUnit.testing(contractualSampling(50))
                    .intent(TestIntent.SMOKE)
                    .assertPasses();
        }
    }
}
