package org.mavai.punit.junit5.testsubjects;

import static org.mavai.punit.api.criterion.Criteria.empirical;
import static org.mavai.punit.api.criterion.Criteria.meeting;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.Experiment;
import org.mavai.punit.api.ProbabilisticTest;
import org.mavai.punit.api.ThresholdOrigin;
import org.mavai.punit.api.criterion.Criteria;
import org.mavai.punit.api.NoFactors;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.runtime.PUnit;

/**
 * Test subjects for {@link org.mavai.punit.junit5.PUnitJunitIntegrationTest}.
 *
 * <p>Lives under {@code testsubjects/} so the project's test-discovery
 * exclusion (per the existing punit convention) keeps these out of
 * normal test runs — they execute only via JUnit Platform TestKit.
 *
 * <p>Two factors-record stand-ins are used: {@link NoFactors} for
 * subjects whose behaviour does not depend on factor values, and
 * {@link GridPoint} for the explore subject, which needs distinct
 * values to populate a grid.
 */
public final class PUnitSubjects {

    private PUnitSubjects() { }

    /**
     * Parameterised factors record for the explore subject — distinct
     * grid points need distinct values.
     */
    public record GridPoint(String label) { }

    private static <F> ServiceContract<F, Integer, Boolean> alwaysPassesContractual() {
        return new ServiceContract<>() {
            @Override public Criteria<Boolean> criteria() {
                return meeting().passRate(0.5);
            }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
                return Outcome.ok(true);
            }
            // Anonymous-class getSimpleName() is "", which BaselineRecord
            // rejects as a blank serviceContractId. Override with a stable id.
            @Override public String id() { return "always-passes-subject"; }
        };
    }

    private static <F> ServiceContract<F, Integer, Boolean> alwaysPassesEmpirical() {
        return new ServiceContract<>() {
            @Override public Criteria<Boolean> criteria() {
                return empirical().passRate();
            }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
                return Outcome.ok(true);
            }
            @Override public String id() { return "always-passes-subject"; }
        };
    }

    private static <F> ServiceContract<F, Integer, Boolean> alwaysFails() {
        return new ServiceContract<>() {
            @Override public Criteria<Boolean> criteria() {
                return meeting().passRate(0.5);
            }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
                return Outcome.fail("nope", "always fails");
            }
            @Override public String id() { return "always-fails-subject"; }
        };
    }

    private static final String CONTRACT_REF_FIXTURE = "Acme API SLA v3.2 §2.1";

    private static <F> ServiceContract<F, Integer, Boolean> alwaysPassesWithContractRef() {
        return new ServiceContract<>() {
            @Override public Criteria<Boolean> criteria() {
                return meeting().<Boolean>passRate(0.5)
                        .contractRef(ThresholdOrigin.SLA, CONTRACT_REF_FIXTURE);
            }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
                return Outcome.ok(true);
            }
            @Override public String id() { return "always-passes-subject"; }
        };
    }

    private static <F> ServiceContract<F, Integer, Boolean> alwaysFailsWithContractRef() {
        return new ServiceContract<>() {
            @Override public Criteria<Boolean> criteria() {
                return meeting().<Boolean>passRate(0.5)
                        .contractRef(ThresholdOrigin.SLA, CONTRACT_REF_FIXTURE);
            }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
                return Outcome.fail("nope", "always fails");
            }
            @Override public String id() { return "always-fails-subject"; }
        };
    }

    private static <F> Sampling<F, Integer, Boolean> sampling(
            ServiceContract<F, Integer, Boolean> serviceContract, int samples) {
        return Sampling.<F, Integer, Boolean>builder()
                .serviceContractFactory(f -> serviceContract)
                .inputs(1, 2, 3)
                .samples(samples)
                .build();
    }

    // ── @ProbabilisticTest subjects ────────────────────────────────

    /** Contractual test that should pass: 100% observed > 50% threshold. */
    public static final class PassingContractualTest {
        @ProbabilisticTest
        void passes() {
            PUnit.testing(sampling(PUnitSubjects.<NoFactors>alwaysPassesContractual(), 20))
                    .assertPasses();
        }
    }

    /** Test that opts in to transparentStats — passes; renderer should emit. */
    public static final class TransparentStatsTest {
        @ProbabilisticTest
        void passesWithTransparentStats() {
            PUnit.testing(sampling(PUnitSubjects.<NoFactors>alwaysPassesContractual(), 20))
                    .transparentStats()
                    .assertPasses();
        }
    }

    /**
     * Passing test that declares a contract reference and opts in to
     * transparentStats — both the rendered verdict and the verbose
     * statistical analysis must surface the reference for audit
     * traceability.
     */
    public static final class ContractRefPassingTest {
        @ProbabilisticTest
        void passesWithContractRef() {
            PUnit.testing(sampling(PUnitSubjects.<NoFactors>alwaysPassesWithContractRef(), 20))
                    .transparentStats()
                    .assertPasses();
        }
    }

    /**
     * Failing test that declares a contract reference. The contract
     * reference must appear in the JUnit assertion message so failure
     * triage can trace the threshold back to the document it derives
     * from.
     */
    public static final class ContractRefFailingTest {
        @ProbabilisticTest
        void failsWithContractRef() {
            PUnit.testing(sampling(PUnitSubjects.<NoFactors>alwaysFailsWithContractRef(), 20))
                    .assertPasses();
        }
    }

    /** Contractual test that should fail: 0% observed < 50% threshold. */
    public static final class FailingContractualTest {
        @ProbabilisticTest
        void fails() {
            PUnit.testing(sampling(PUnitSubjects.<NoFactors>alwaysFails(), 20))
                    .assertPasses();
        }
    }

    /**
     * Empirical test against the EMPTY baseline provider — no
     * baseline resolves, criterion yields INCONCLUSIVE → JUnit
     * aborted (skipped) test.
     */
    public static final class InconclusiveEmpiricalTest {
        @ProbabilisticTest
        void inconclusive() {
            PUnit.testing(sampling(PUnitSubjects.<NoFactors>alwaysPassesEmpirical(), 20))
                    .assertPasses();
        }
    }

    // ── @Experiment subjects ───────────────────────────────────────

    /** A measure experiment — produces a baseline; .run() returns normally. */
    public static final class PassingMeasureExperiment {
        @Experiment
        void measure() {
            PUnit.measuring(sampling(PUnitSubjects.<NoFactors>alwaysPassesContractual(), 100)).run();
        }
    }

    /** An explore experiment — produces a grid; .run() returns normally. */
    public static final class PassingExploreExperiment {
        @Experiment
        void explore() {
            PUnit.exploring(sampling(PUnitSubjects.<GridPoint>alwaysPassesContractual(), 5))
                    .grid(new GridPoint("alt-1"), new GridPoint("alt-2"))
                    .run();
        }
    }

    // ── Empirical-supplier subjects (PUnit.testing(Supplier)) ──────

    /**
     * The baseline supplier — a non-test method returning a built
     * {@link org.mavai.punit.api.spec.Experiment} for the
     * {@link PUnit#testing(java.util.function.Supplier)} entry point.
     * The same builder produces both a baseline-running
     * {@code @Experiment} method and the supplier the test consumes.
     */
    public static final class EmpiricalSupplierTest {
        private org.mavai.punit.api.spec.Experiment baseline() {
            return PUnit.measuring(sampling(PUnitSubjects.<NoFactors>alwaysPassesEmpirical(), 100)).build();
        }

        @ProbabilisticTest
        void empiricalDerivedFromBaseline() {
            // No baseline file on disk → BaselineProvider.EMPTY → INCONCLUSIVE.
            // The point of this subject is to exercise the supplier-derivation
            // path: factors and sampling come from baseline(), only samples is
            // specified at the test side.
            PUnit.testing(this::baseline)
                    .samples(20)
                    .assertPasses();
        }
    }

    /**
     * Empirical-supplier with a non-MEASURE baseline — must reject
     * with an IllegalArgumentException at compose time.
     */
    public static final class EmpiricalSupplierBadKindTest {
        private org.mavai.punit.api.spec.Experiment exploreBaseline() {
            return PUnit.exploring(sampling(PUnitSubjects.<GridPoint>alwaysPassesContractual(), 5))
                    .grid(new GridPoint("alt"))
                    .build();
        }

        @ProbabilisticTest
        void rejectsNonMeasureSupplier() {
            PUnit.testing(this::exploreBaseline)
                    .samples(20)
                    .assertPasses();
        }
    }
}
