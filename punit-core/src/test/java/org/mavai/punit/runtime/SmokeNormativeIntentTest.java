package org.mavai.punit.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mavai.outcome.Outcome;
import org.mavai.punit.api.NoFactors;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.ThresholdOrigin;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.criterion.Criteria;
import org.mavai.punit.api.spec.ConfigurationRefusedException;
import org.mavai.punit.internal.reporting.VerdictSinkBus;
import org.mavai.punit.statistics.DecisionRule;
import org.mavai.punit.verdict.CriterionRow;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.TerminationReason;
import org.opentest4j.AssertionFailedError;

/**
 * A SMOKE test of a declared requirement too small to demonstrate it:
 * the run goes ahead and is decided by {@code compliance/smoke-exact-binomial}
 * (Statistical Companion §5.7.3), FAIL only on evidence that the service
 * falls short. At a 0.99 POLICY requirement, alpha 0.05 and 100 samples
 * the rule's required-pass is 97: a service that always succeeds stops
 * at its 97th pass with success guaranteed, and the fourth miss makes
 * failure inevitable. VERIFICATION refuses the same design.
 */
@DisplayName("SMOKE intent on a declared requirement decides by its own rule")
class SmokeNormativeIntentTest {

    private static final double REQUIREMENT = 0.99;
    private static final int SAMPLES = 100;

    private final List<ProbabilisticTestVerdict> records = new ArrayList<>();

    @BeforeEach
    void captureVerdicts() {
        VerdictSinkBus.replaceAll(records::add);
    }

    @AfterEach
    void releaseVerdicts() {
        VerdictSinkBus.reset();
    }

    private static ServiceContract<NoFactors, Integer, Boolean> service(
            AtomicInteger invocations, int leadingMisses) {
        return new ServiceContract<>() {
            @Override public String id() { return "harmful-advice-is-rejected"; }
            @Override public Criteria<Boolean> criteria() {
                return Criteria.meeting().<Boolean>passRate(REQUIREMENT)
                        .contractRef(ThresholdOrigin.POLICY, "safety-policy");
            }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
                return invocations.incrementAndGet() <= leadingMisses
                        ? Outcome.fail("harmful", "harmful advice was not rejected")
                        : Outcome.ok(Boolean.TRUE);
            }
        };
    }

    private static PUnit.TestBuilder<NoFactors, Integer, Boolean> test(
            AtomicInteger invocations, int leadingMisses) {
        ServiceContract<NoFactors, Integer, Boolean> contract = service(invocations, leadingMisses);
        return PUnit.testing(Sampling.<NoFactors, Integer, Boolean>builder()
                .serviceContractFactory(f -> contract)
                .inputs(1, 2, 3)
                .samples(SAMPLES)
                .build());
    }

    private ProbabilisticTestVerdict onlyRecord() {
        assertThat(records).hasSize(1);
        return records.get(0);
    }

    private static CriterionRow onlyRow(ProbabilisticTestVerdict verdict) {
        List<CriterionRow> rows = verdict.perCriterion().orElseThrow().criteria();
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    @Test
    @DisplayName("an always-succeeding service runs past the first sample and PASSes at success-guaranteed")
    void alwaysSucceedingServicePasses() {
        AtomicInteger invocations = new AtomicInteger();

        test(invocations, 0).intent(TestIntent.SMOKE).assertPasses();

        ProbabilisticTestVerdict verdict = onlyRecord();
        assertThat(invocations).hasValue(97);
        assertThat(verdict.termination().reason())
                .isNotEqualTo(TerminationReason.IMPOSSIBILITY)
                .isEqualTo(TerminationReason.SUCCESS_GUARANTEED);
        assertThat(verdict.execution().samplesExecuted()).isEqualTo(97);
        assertThat(verdict.execution().plannedSamples()).isEqualTo(SAMPLES);
        assertThat(verdict.execution().intent()).isEqualTo(TestIntent.SMOKE);
        assertThat(verdict.punitVerdict().name()).isEqualTo("PASS");
        assertThat(verdict.decision().decisionRule())
                .contains(DecisionRule.COMPLIANCE_SMOKE_EXACT_BINOMIAL);
        CriterionRow row = onlyRow(verdict);
        assertThat(row.decisionRule()).contains(DecisionRule.COMPLIANCE_SMOKE_EXACT_BINOMIAL);
        assertThat(row.decisionRule().orElseThrow().id()).isEqualTo("compliance/smoke-exact-binomial");
        assertThat(row.requiredPass()).isEqualTo(OptionalInt.of(97));
    }

    @Test
    @DisplayName("the fourth miss makes failure inevitable: FAIL")
    void fourMissesFail() {
        AtomicInteger invocations = new AtomicInteger();

        Throwable thrown = catchThrowable(() ->
                test(invocations, 4).intent(TestIntent.SMOKE).assertPasses());

        assertThat(thrown).isInstanceOf(AssertionFailedError.class);
        assertThat(thrown.getMessage())
                .contains("compliance/smoke-exact-binomial")
                .contains("required-pass=97");
        ProbabilisticTestVerdict verdict = onlyRecord();
        assertThat(invocations).hasValue(4);
        assertThat(verdict.termination().reason()).isEqualTo(TerminationReason.IMPOSSIBILITY);
        assertThat(verdict.execution().intent()).isEqualTo(TestIntent.SMOKE);
        assertThat(verdict.punitVerdict().name()).isEqualTo("FAIL");
        assertThat(onlyRow(verdict).decisionRule())
                .contains(DecisionRule.COMPLIANCE_SMOKE_EXACT_BINOMIAL);
    }

    @Test
    @DisplayName("three misses in 100 still PASS: no evidence of falling short")
    void threeMissesPass() {
        AtomicInteger invocations = new AtomicInteger();

        test(invocations, 3).intent(TestIntent.SMOKE).assertPasses();

        ProbabilisticTestVerdict verdict = onlyRecord();
        assertThat(invocations).hasValue(SAMPLES);
        assertThat(verdict.punitVerdict().name()).isEqualTo("PASS");
        assertThat(verdict.execution().successes()).isEqualTo(97);
    }

    @Test
    @DisplayName("VERIFICATION still refuses the same design before any sample")
    void verificationStillRefuses() {
        AtomicInteger invocations = new AtomicInteger();

        Throwable thrown = catchThrowable(() -> test(invocations, 0).assertPasses());

        assertThat(thrown).isInstanceOf(ConfigurationRefusedException.class);
        assertThat(thrown.getMessage()).contains("COMPLIANCE_INFEASIBLE");
        assertThat(invocations).hasValue(0);
    }
}
