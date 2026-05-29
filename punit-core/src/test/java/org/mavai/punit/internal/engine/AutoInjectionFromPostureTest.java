package org.mavai.punit.internal.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.ThresholdOrigin;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.criterion.Criteria;
import static org.mavai.punit.api.criterion.Criteria.meeting;
import org.mavai.punit.api.spec.ProbabilisticTest;
import org.mavai.punit.api.spec.ProbabilisticTestResult;
import org.mavai.punit.api.spec.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Auto-injection of spec-criteria from contract posture")
class AutoInjectionFromPostureTest {

    record Factors(String label) { }

    private static final Factors FACTORS = new Factors("auto-inject");

    private static <O> Sampling<Factors, Integer, O> sampling(
            ServiceContract<Factors, Integer, O> contract) {
        return Sampling.<Factors, Integer, O>builder()
                .serviceContractFactory(f -> contract)
                .inputs(1, 2, 3)
                .samples(20)
                .build();
    }

    @Test
    @DisplayName("contract with .meeting() posture and no test-builder criterion yields a PASS via auto-injected PassRate.meeting")
    void contractualPostureAutoInjects() {
        ServiceContract<Factors, Integer, Boolean> alwaysPasses = new ServiceContract<>() {
            @Override public String id() { return "always-passes"; }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker t) {
                return Outcome.ok(true);
            }
            @Override public Criteria<Boolean> criteria() {
                return meeting().passRate(0.95);
            }
        };

        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling(alwaysPasses), FACTORS)
                .build();

        var result = (ProbabilisticTestResult) new Engine().run(spec);
        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
        assertThat(result.criterionResults()).hasSize(1);
        assertThat(result.criterionResults().get(0).result().criterionName())
                .isEqualTo("bernoulli-pass-rate");
    }

    @Test
    @DisplayName("contract with .meeting() at high threshold and a failing service yields FAIL via auto-injected criterion")
    void contractualPostureAutoInjectsAndCanFail() {
        ServiceContract<Factors, Integer, Boolean> alwaysFails = new ServiceContract<>() {
            @Override public String id() { return "always-fails"; }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker t) {
                return Outcome.fail("nope", "never passes");
            }
            @Override public Criteria<Boolean> criteria() {
                return meeting().passRate(0.95);
            }
        };

        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling(alwaysFails), FACTORS)
                .build();

        var result = (ProbabilisticTestResult) new Engine().run(spec);
        assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
    }

}
