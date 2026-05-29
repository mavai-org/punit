package org.mavai.punit.api.spec;

import static org.mavai.punit.api.criterion.Criteria.meeting;
import org.mavai.punit.api.criterion.Criteria;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.ServiceContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProbabilisticTest intent")
class ProbabilisticTestIntentTest {

    record Factors(String label) {}

    private static final ServiceContract<Factors, String, String> ECHO = new ServiceContract<>() {
        @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input);
        }
        @Override public Criteria<String> criteria() {
            return meeting().<String>zeroFailures();
        }

    };

    private static Sampling<Factors, String, String> sampling() {
        return Sampling.<Factors, String, String>builder()
                .serviceContractFactory(f -> ECHO)
                .inputs("a")
                .samples(10)
                .build();
    }

    @Test
    @DisplayName("default intent is VERIFICATION")
    void defaultIntentIsVerification() {
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling(), new Factors("m"))
                .build();

        assertThat(spec.intent()).isEqualTo(TestIntent.VERIFICATION);
    }

    @Test
    @DisplayName(".intent(SMOKE) overrides the default")
    void intentSmokeOverridesDefault() {
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling(), new Factors("m"))
                .intent(TestIntent.SMOKE)
                .build();

        assertThat(spec.intent()).isEqualTo(TestIntent.SMOKE);
    }

    @Test
    @DisplayName(".intent(VERIFICATION) is permitted (idempotent with default)")
    void intentVerificationIsPermitted() {
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling(), new Factors("m"))
                .intent(TestIntent.VERIFICATION)
                .build();

        assertThat(spec.intent()).isEqualTo(TestIntent.VERIFICATION);
    }

    @Test
    @DisplayName(".intent(null) is rejected")
    void intentNullIsRejected() {
        var builder = ProbabilisticTest.testing(sampling(), new Factors("m"));
        assertThatNullPointerException().isThrownBy(() -> builder.intent(null));
    }
}
