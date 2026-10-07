package org.mavai.punit.api.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import org.mavai.punit.api.spec.AssertionEnforcement.Dimension;
import org.mavai.punit.api.spec.VerdictComposition.Decided;
import org.mavai.punit.api.spec.VerdictComposition.Trigger;
import org.mavai.punit.statistics.DecisionRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("VerdictComposition — the test verdict over the enforced dimensions")
class TestVerdictCompositionTest {

    private static final AssertionEnforcement NONE = AssertionEnforcement.ALL_ENFORCED;
    private static final AssertionEnforcement FUNCTIONAL = AssertionEnforcement.advisory(Dimension.FUNCTIONAL);
    private static final AssertionEnforcement LATENCY = AssertionEnforcement.advisory(Dimension.LATENCY);
    private static final AssertionEnforcement BOTH =
            AssertionEnforcement.advisory(Dimension.FUNCTIONAL, Dimension.LATENCY);

    private static Decided criterion(Verdict v) {
        return new Decided("c", v, Optional.of(DecisionRule.REGRESSION_FISHER), OptionalDouble.of(0.05));
    }

    private static Decided ceiling(Verdict v) {
        return new Decided("p95", v, Optional.of(DecisionRule.LATENCY_COMPLIANCE_EXACT_BINOMIAL),
                OptionalDouble.of(0.05));
    }

    @Test
    @DisplayName("unset: both dimensions enforced and composed")
    void bothEnforced() {
        VerdictComposition c = VerdictComposition.compose(
                List.of(criterion(Verdict.PASS)), List.of(ceiling(Verdict.FAIL)), NONE);
        assertThat(c.functionalMode()).contains(EnforcementMode.ENFORCED);
        assertThat(c.latencyMode()).contains(EnforcementMode.ENFORCED);
        assertThat(c.testVerdict()).isEqualTo(Verdict.FAIL);
        assertThat(c.triggering()).containsExactly(new Trigger(Trigger.Kind.LATENCY, "p95"));
        assertThat(c.falseComplianceEnvelope()).hasValue(0.05);
        assertThat(c.falseDegradationSignalEnvelope()).hasValue(0.05);
    }

    @Test
    @DisplayName("latency advisory: its FAIL is reported, never enters V_test, the triggers or the envelopes")
    void latencyAdvisory() {
        VerdictComposition c = VerdictComposition.compose(
                List.of(criterion(Verdict.PASS)), List.of(ceiling(Verdict.FAIL)), LATENCY);
        assertThat(c.latencyVerdict()).contains(Verdict.FAIL);
        assertThat(c.latencyMode()).contains(EnforcementMode.ADVISORY);
        assertThat(c.latencyEnforced()).isFalse();
        assertThat(c.testVerdict()).isEqualTo(Verdict.PASS);
        assertThat(c.triggering()).isEmpty();
        assertThat(c.falseComplianceEnvelope()).isEmpty();
        assertThat(c.falseDegradationSignalEnvelope()).hasValue(0.05);
    }

    @Test
    @DisplayName("functional advisory: V_test is V_latency, triggered by the constraint alone")
    void functionalAdvisory() {
        VerdictComposition c = VerdictComposition.compose(
                List.of(criterion(Verdict.FAIL)), List.of(ceiling(Verdict.INCONCLUSIVE)), FUNCTIONAL);
        assertThat(c.rateVerdict()).contains(Verdict.FAIL);
        assertThat(c.functionalMode()).contains(EnforcementMode.ADVISORY);
        assertThat(c.testVerdict()).isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(c.triggering()).containsExactly(new Trigger(Trigger.Kind.LATENCY, "p95"));
        assertThat(c.falseDegradationSignalEnvelope()).isEmpty();
    }

    @Test
    @DisplayName("both advisory: no dimension binds and V_test is PASS, with no envelope")
    void bothAdvisory() {
        VerdictComposition c = VerdictComposition.compose(
                List.of(criterion(Verdict.FAIL)), List.of(ceiling(Verdict.FAIL)), BOTH);
        assertThat(c.rateVerdict()).contains(Verdict.FAIL);
        assertThat(c.latencyVerdict()).contains(Verdict.FAIL);
        assertThat(c.testVerdict()).isEqualTo(Verdict.PASS);
        assertThat(c.triggering()).isEmpty();
        assertThat(c.falseComplianceEnvelope()).isEmpty();
        assertThat(c.falseDegradationSignalEnvelope()).isEmpty();
    }

    @Test
    @DisplayName("an absent dimension has no verdict and no mode, whatever the setting")
    void absentDimensionHasNoMode() {
        VerdictComposition c = VerdictComposition.compose(
                List.of(), List.of(ceiling(Verdict.FAIL)), FUNCTIONAL);
        assertThat(c.rateVerdict()).isEmpty();
        assertThat(c.functionalMode()).isEmpty();
        assertThat(c.testVerdict()).isEqualTo(Verdict.FAIL);
    }

    @Test
    @DisplayName("the only dimension advisory: V_test is PASS")
    void onlyDimensionAdvisory() {
        VerdictComposition c = VerdictComposition.compose(
                List.of(criterion(Verdict.FAIL)), List.of(), FUNCTIONAL);
        assertThat(c.testVerdict()).isEqualTo(Verdict.PASS);
        assertThat(c.latencyMode()).isEmpty();
    }

    @Test
    @DisplayName("nothing to compose is refused")
    void nothingToCompose() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> VerdictComposition.compose(List.of(), List.of(), NONE));
    }
}
