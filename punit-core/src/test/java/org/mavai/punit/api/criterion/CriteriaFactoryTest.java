package org.mavai.punit.api.criterion;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mavai.punit.api.PercentileKey.P50;
import static org.mavai.punit.api.PercentileKey.P95;
import static org.mavai.punit.api.PercentileKey.P99;
import static org.mavai.punit.api.ThresholdOrigin.POLICY;
import static org.mavai.punit.api.ThresholdOrigin.SLA;
import static org.mavai.punit.api.ThresholdOrigin.UNSPECIFIED;
import static org.mavai.punit.api.criterion.Criteria.empirical;
import static org.mavai.punit.api.criterion.Criteria.meeting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Exercises the value-form authoring surface — the no-arg
 * {@link Criteria#meeting()} / {@link Criteria#empirical()} factories
 * and the kind-selector first chain methods
 * ({@code .passRate}, {@code .zeroFailures}, {@code .atMost}).
 */
@DisplayName("Criteria.meeting() / Criteria.empirical() — value-form factories")
class CriteriaFactoryTest {

    @Nested
    @DisplayName("Contractual chain — meeting()")
    class ContractualChain {

        @Test
        @DisplayName("meeting().passRate(rate) yields a STATISTICAL_CONTRACTUAL decl at UNSPECIFIED origin")
        void passRateShape() {
            CriterionDecl<String> decl = meeting().passRate(0.85);

            assertThat(decl.posture().kind())
                    .isEqualTo(CriterionPosture.Kind.STATISTICAL_CONTRACTUAL);
            assertThat(decl.posture().origin()).hasValue(UNSPECIFIED);
            assertThat(decl.posture().threshold().getAsDouble()).isEqualTo(0.85);
        }

        @Test
        @DisplayName("meeting().zeroFailures() yields a ZERO_FAILURES decl at UNSPECIFIED origin")
        void zeroFailuresShape() {
            CriterionDecl<String> decl = meeting().zeroFailures();

            assertThat(decl.posture().kind()).isEqualTo(CriterionPosture.Kind.ZERO_FAILURES);
            assertThat(decl.posture().origin()).hasValue(UNSPECIFIED);
        }

        @Test
        @DisplayName(".contractRef(origin, ref) updates both the origin and the reference")
        void contractRefUpdatesOriginAndRef() {
            CriterionDecl<String> decl = meeting().<String>passRate(0.9999)
                    .contractRef(SLA, "Payment Provider SLA v2.3, §4.1");

            assertThat(decl.posture().origin()).hasValue(SLA);
            assertThat(decl.posture().contractRef())
                    .hasValue("Payment Provider SLA v2.3, §4.1");
        }

        @Test
        @DisplayName(".contractRef(SLA, ref) on zero-failures carries origin + ref through")
        void zeroFailuresContractRef() {
            CriterionDecl<String> decl = meeting().<String>zeroFailures()
                    .contractRef(POLICY, "Security Policy §1.2")
                    .where("no-secret-key", v -> !v.contains("AKIA"));

            assertThat(decl.posture().origin()).hasValue(POLICY);
            assertThat(decl.posture().contractRef()).hasValue("Security Policy §1.2");
            assertThat(decl.postconditions()).hasSize(1);
        }

        @Test
        @DisplayName("postconditions and refinements chain through after the kind selector")
        void postconditionsAndRefinementsChain() {
            CriterionDecl<String> decl = meeting().<String>passRate(0.85)
                    .name("parseable-json")
                    .where("parseable", v -> v.startsWith("{"));

            assertThat(decl.name()).hasValue("parseable-json");
            assertThat(decl.postconditions()).hasSize(1);
            assertThat(decl.postconditions().get(0).name()).isEqualTo("parseable");
        }
    }

    @Nested
    @DisplayName("Empirical chain — empirical()")
    class EmpiricalChain {

        @Test
        @DisplayName("empirical().passRate() yields a STATISTICAL_EMPIRICAL decl")
        void passRateShape() {
            CriterionDecl<String> decl = empirical().passRate();

            assertThat(decl.posture().kind())
                    .isEqualTo(CriterionPosture.Kind.STATISTICAL_EMPIRICAL);
        }

        @Test
        @DisplayName("empirical refinements chain through")
        void empiricalRefinementsChain() {
            CriterionDecl<String> decl = empirical().<String>passRate()
                    .atConfidence(0.99)
                    .detectingMde(0.02)
                    .atPower(0.95);

            assertThat(decl.posture().confidenceFloor()).isPresent();
            assertThat(decl.posture().mde()).isPresent();
            assertThat(decl.posture().power()).isPresent();
        }

        @Test
        @DisplayName(".tolerating(rate) declares the risk-driven form of confidence-first")
        void toleratingDeclaresRiskDriven() {
            CriterionDecl<String> decl = empirical().<String>passRate()
                    .tolerating(0.93)
                    .atConfidence(0.95)
                    .atPower(0.80);

            assertThat(decl.posture().toleratedRate()).hasValue(0.93);
            assertThat(decl.posture().isRiskDriven()).isTrue();
            assertThat(decl.posture().isConfidenceFirst()).isFalse();
        }

        @Test
        @DisplayName(".tolerating alone is a complete declaration — power defaults downstream")
        void toleratingAloneIsComplete() {
            CriterionDecl<String> decl = empirical().<String>passRate()
                    .tolerating(0.93);

            decl.posture().validate();
            assertThat(decl.posture().isRiskDriven()).isTrue();
        }

        @Test
        @DisplayName(".atPower pairs with .tolerating — no MDE demanded")
        void atPowerPairsWithTolerating() {
            CriterionDecl<String> decl = empirical().<String>passRate()
                    .tolerating(0.93)
                    .atPower(0.90);

            decl.posture().validate();
            assertThat(decl.posture().power()).hasValue(0.90);
        }

        @Test
        @DisplayName(".tolerating and .detectingMde are mutually exclusive, both ways")
        void toleratingAndMdeAreMutuallyExclusive() {
            assertThatThrownBy(() -> empirical().<String>passRate()
                    .detectingMde(0.05)
                    .tolerating(0.93))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("either relatively")
                    .hasMessageContaining("or absolutely");
            assertThatThrownBy(() -> empirical().<String>passRate()
                    .tolerating(0.93)
                    .detectingMde(0.05))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("never both");
        }

        @Test
        @DisplayName(".tolerating rejects composition with a declared threshold")
        void toleratingRejectsContractualPosture() {
            assertThatThrownBy(() -> meeting().<String>passRate(0.9)
                    .tolerating(0.93))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot compose with .meeting");
        }

        @Test
        @DisplayName(".tolerating rejects rates outside (0, 1)")
        void toleratingRejectsOutOfRangeRates() {
            assertThatThrownBy(() -> empirical().<String>passRate().tolerating(0.0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> empirical().<String>passRate().tolerating(1.0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Contractual latency — meeting().atMost(...)")
    class ContractualLatency {

        @Test
        @DisplayName("meeting().atMost(P95, ofSeconds(1)) yields a present contractual latency criterion")
        void singlePercentileShape() {
            LatencyCriterion decl = meeting().atMost(P95, ofSeconds(1));

            assertThat(decl.isPresent()).isTrue();
            assertThat(decl.toRuntime().id()).isEqualTo(LatencyCriterion.ID);
        }

        @Test
        @DisplayName("chained .atMost(...) accepts strictly increasing durations across rising percentiles")
        void chainedAtMostStrictlyIncreasing() {
            LatencyCriterion decl = meeting()
                    .atMost(P95, ofSeconds(1))
                    .atMost(P99, ofSeconds(5))
                    .contractRef(SLA, "Acme Payment SLA v3.2 §4.2");

            assertThat(decl.isPresent()).isTrue();
        }

        @Test
        @DisplayName("chained .atMost(...) accepts equal durations (uniform cap)")
        void chainedAtMostEqualDurations() {
            LatencyCriterion decl = meeting()
                    .atMost(P95, ofMillis(500))
                    .atMost(P99, ofMillis(500));

            assertThat(decl.isPresent()).isTrue();
        }

        @Test
        @DisplayName("chained .atMost(...) accepts out-of-order declaration with monotone values")
        void chainedAtMostOutOfDeclarationOrder() {
            LatencyCriterion decl = meeting()
                    .atMost(P99, ofSeconds(5))
                    .atMost(P95, ofSeconds(1));

            assertThat(decl.isPresent()).isTrue();
        }

        @Test
        @DisplayName("monotonicity violation: higher percentile assigned a lower duration is rejected")
        void monotonicityViolationHigherPercentileLowerDuration() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> meeting()
                            .atMost(P95, ofMillis(1000))
                            .atMost(P99, ofMillis(800)))
                    .withMessageContaining("P99")
                    .withMessageContaining("P95")
                    .withMessageContaining("800ms")
                    .withMessageContaining("1000ms")
                    .withMessageContaining("unreachable");
        }

        @Test
        @DisplayName("monotonicity violation: out-of-order declaration is still rejected")
        void monotonicityViolationOutOfOrder() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> meeting()
                            .atMost(P99, ofMillis(800))
                            .atMost(P95, ofMillis(1000)))
                    .withMessageContaining("P99")
                    .withMessageContaining("P95");
        }

        @Test
        @DisplayName("monotonicity violation across three percentiles is rejected")
        void monotonicityViolationThreePercentiles() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> meeting()
                            .atMost(P50, ofMillis(100))
                            .atMost(P95, ofMillis(500))
                            .atMost(P99, ofMillis(300)));
        }

        @Test
        @DisplayName("duplicate percentile is rejected")
        void duplicatePercentileRejected() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> meeting()
                            .atMost(P95, ofSeconds(1))
                            .atMost(P95, ofSeconds(2)))
                    .withMessageContaining("duplicate");
        }

        @Test
        @DisplayName(".atMost(P95) with no duration is rejected on a contractual chain")
        void atMostWithoutDurationRejectedOnContractual() {
            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> meeting()
                            .atMost(P95, ofSeconds(1))
                            .atMost(P99))
                    .withMessageContaining("contractual");
        }
    }

    @Nested
    @DisplayName("Empirical latency — empirical().atMost(...)")
    class EmpiricalLatency {

        @Test
        @DisplayName("empirical().atMost(P95) yields a present empirical latency criterion")
        void singlePercentileShape() {
            LatencyCriterion decl = empirical().atMost(P95);

            assertThat(decl.isPresent()).isTrue();
            assertThat(decl.toRuntime().id()).isEqualTo(LatencyCriterion.ID);
        }

        @Test
        @DisplayName("chained .atMost(...) accumulates asserted percentiles")
        void chainedAtMostAccumulates() {
            LatencyCriterion decl = empirical().atMost(P95).atMost(P99).atConfidence(0.99);
            assertThat(decl.isPresent()).isTrue();
        }

        @Test
        @DisplayName(".atMost(P95, duration) is rejected on an empirical chain")
        void atMostWithDurationRejectedOnEmpirical() {
            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> empirical()
                            .atMost(P95)
                            .atMost(P99, ofSeconds(5)))
                    .withMessageContaining("empirical");
        }

        @Test
        @DisplayName("duplicate percentile is rejected")
        void duplicatePercentileRejected() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> empirical().atMost(P95).atMost(P95));
        }

        @Test
        @DisplayName(".contractRef(SLA, ref) is rejected on an empirical latency criterion")
        void contractRefRejectedOnEmpirical() {
            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> empirical().atMost(P95).contractRef(SLA, "doc"))
                    .withMessageContaining("empirical");
        }
    }
}
