package org.mavai.punit.sentinel;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.NoFactors;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.criterion.Criteria;
import org.mavai.punit.api.spec.AssertionEnforcement;
import org.mavai.punit.api.spec.EnforcementMode;
import org.mavai.punit.api.spec.Verdict;
import org.mavai.punit.verdict.PUnitVerdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A sentinel run honours the advisory setting exactly as a test run
 * does: the sentinel executes the same {@code assertPasses()} path.
 */
@DisplayName("Sentinel — the advisory setting")
class SentinelAdvisoryTest {

    @AfterEach
    void clearProperty() {
        System.clearProperty(AssertionEnforcement.PROPERTY);
    }

    /** A real run: 30 of 60 samples pass against a 0.9 requirement. */
    public static class FailingRequirement {

        public FailingRequirement() { }

        public void run() {
            AtomicInteger invoked = new AtomicInteger();
            Criteria<Boolean> criteria = Criteria.meeting().<Boolean>passRate(0.9).name("accuracy")
                    .satisfies("output is true", out ->
                            out ? Outcome.ok(out) : Outcome.fail("sentinel", "scripted failure"));
            org.mavai.punit.runtime.PUnit.testing(Sampling.<NoFactors, Integer, Boolean>builder()
                            .serviceContractFactory(f -> new ServiceContract<NoFactors, Integer, Boolean>() {
                                @Override public Criteria<Boolean> criteria() { return criteria; }
                                @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
                                    return Outcome.ok(invoked.getAndIncrement() < 30);
                                }
                                @Override public String id() { return "sentinel-advisory"; }
                            })
                            .inputs(1, 2, 3)
                            .samples(60)
                            .build())
                    .assertPasses();
        }
    }

    private static ProbabilisticTestVerdict execute() throws Exception {
        Method method = FailingRequirement.class.getDeclaredMethod("run");
        SentinelExecutor.Outcome outcome = new SentinelExecutor().execute(FailingRequirement.class, method);
        assertThat(outcome.isDefect()).isFalse();
        return outcome.verdict().orElseThrow();
    }

    @Test
    @DisplayName("unset, the failing requirement fails the sentinel run")
    void enforcedByDefault() throws Exception {
        ProbabilisticTestVerdict verdict = execute();
        assertThat(verdict.punitVerdict()).isEqualTo(PUnitVerdict.FAIL);
        assertThat(verdict.perCriterion().orElseThrow().mode()).isEqualTo(EnforcementMode.ENFORCED);
    }

    @Test
    @DisplayName("functional advisory: the requirement is decided FAIL and reported, and the run passes")
    void functionalAdvisory() throws Exception {
        System.setProperty(AssertionEnforcement.PROPERTY, "functional");
        ProbabilisticTestVerdict verdict = execute();
        assertThat(verdict.punitVerdict()).isEqualTo(PUnitVerdict.PASS);
        assertThat(verdict.perCriterion().orElseThrow().composite()).isEqualTo(Verdict.FAIL);
        assertThat(verdict.perCriterion().orElseThrow().mode()).isEqualTo(EnforcementMode.ADVISORY);
    }
}
