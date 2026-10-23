package org.mavai.punit.api.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mavai.punit.statistics.DecisionRule;

@DisplayName("VerdictComposition — Type-I envelopes by direction")
class VerdictCompositionEnvelopeTest {

    private static VerdictComposition.Decided decided(String id, DecisionRule rule, double alpha) {
        return new VerdictComposition.Decided(id, Verdict.PASS, Optional.of(rule), OptionalDouble.of(alpha));
    }

    @Test
    @DisplayName("a SMOKE decision enters neither envelope, pass rate or latency")
    void smokeEntersNeitherEnvelope() {
        VerdictComposition c = VerdictComposition.compose(
                List.of(decided("smoke-rate", DecisionRule.COMPLIANCE_SMOKE_EXACT_BINOMIAL, 0.05)),
                List.of(decided("smoke-p95", DecisionRule.LATENCY_SMOKE_COMPLIANCE_EXACT_BINOMIAL, 0.05)),
                AssertionEnforcement.parse(""));

        assertThat(c.falseComplianceEnvelope()).isEmpty();
        assertThat(c.falseDegradationSignalEnvelope()).isEmpty();
        assertThat(DecisionRule.COMPLIANCE_SMOKE_EXACT_BINOMIAL.direction())
                .isEqualTo(DecisionRule.Direction.NONE);
        assertThat(DecisionRule.LATENCY_SMOKE_COMPLIANCE_EXACT_BINOMIAL.direction())
                .isEqualTo(DecisionRule.Direction.NONE);
    }

    @Test
    @DisplayName("beside a VERIFICATION requirement and a regression criterion, only those two enter")
    void smokeBesideOthers() {
        VerdictComposition c = VerdictComposition.compose(
                List.of(decided("verified", DecisionRule.COMPLIANCE_EXACT_BINOMIAL, 0.01),
                        decided("smoke", DecisionRule.COMPLIANCE_SMOKE_EXACT_BINOMIAL, 0.05),
                        decided("regression", DecisionRule.REGRESSION_FISHER, 0.05)),
                List.of(),
                AssertionEnforcement.parse(""));

        assertThat(c.falseComplianceEnvelope().getAsDouble()).isCloseTo(0.01, within(1e-12));
        assertThat(c.falseDegradationSignalEnvelope().getAsDouble()).isCloseTo(0.05, within(1e-12));
    }
}
