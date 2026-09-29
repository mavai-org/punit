package org.mavai.punit.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ComplianceEvidenceEvaluator}.
 *
 * <p>A sample is undersized for compliance when no outcome of its size —
 * not even zero failures — could demonstrate the requirement under
 * {@code compliance/exact-binomial}: {@code n < ⌈log α / log p₀⌉}. For
 * p₀ = 0.9999 at α = 0.05 that minimum is 29,956.
 */
class ComplianceEvidenceEvaluatorTest {

    @Nested
    @DisplayName("isUndersized()")
    class IsUndersized {

        @Test
        @DisplayName("p0=0.9999 with N=200 is undersized")
        void undersizedAt200SamplesFor9999() {
            assertThat(ComplianceEvidenceEvaluator.isUndersized(200, 0.9999)).isTrue();
        }

        @Test
        @DisplayName("p0=0.9999 with N=500 is undersized")
        void undersizedAt500SamplesFor9999() {
            assertThat(ComplianceEvidenceEvaluator.isUndersized(500, 0.9999)).isTrue();
        }

        @Test
        @DisplayName("p0=0.9999 with N=10000 is still undersized — the feasibility minimum is 29956")
        void undersizedAt10000SamplesFor9999() {
            // At α=0.05 a pass needs 0.9999^n ≤ 0.05: n ≥ 29,956.
            assertThat(ComplianceEvidenceEvaluator.isUndersized(10000, 0.9999)).isTrue();
        }

        @Test
        @DisplayName("p0=0.95 with N=200 is NOT undersized")
        void sufficientAt200SamplesFor95() {
            // Wilson lower bound at n=200 with zero failures: 200/(200+9.55) ≈ 0.954 ≥ 0.95
            assertThat(ComplianceEvidenceEvaluator.isUndersized(200, 0.95)).isFalse();
        }

        @Test
        @DisplayName("p0=0.9 with N=200 is NOT undersized")
        void sufficientAt200SamplesFor90() {
            assertThat(ComplianceEvidenceEvaluator.isUndersized(200, 0.9)).isFalse();
        }

        @Test
        @DisplayName("very large N is sufficient even for extreme targets")
        void sufficientAtLargeN() {
            assertThat(ComplianceEvidenceEvaluator.isUndersized(100_000, 0.9999)).isFalse();
        }

        @Test
        @DisplayName("returns false for zero samples")
        void falseForZeroSamples() {
            assertThat(ComplianceEvidenceEvaluator.isUndersized(0, 0.9999)).isFalse();
        }

        @Test
        @DisplayName("returns false for negative samples")
        void falseForNegativeSamples() {
            assertThat(ComplianceEvidenceEvaluator.isUndersized(-10, 0.9999)).isFalse();
        }

        @Test
        @DisplayName("returns false for target at boundary 0.0")
        void falseForTargetAtZero() {
            assertThat(ComplianceEvidenceEvaluator.isUndersized(100, 0.0)).isFalse();
        }

        @Test
        @DisplayName("returns false for target at boundary 1.0")
        void falseForTargetAtOne() {
            assertThat(ComplianceEvidenceEvaluator.isUndersized(100, 1.0)).isFalse();
        }
    }

    @Nested
    @DisplayName("isUndersized() with custom alpha")
    class IsUndersizedWithAlpha {

        @Test
        @DisplayName("less strict alpha makes more samples sufficient")
        void lessStrictAlpha() {
            // With α=0.05, z≈1.645, z²≈2.706. Lower bound at n=200: 200/202.706 ≈ 0.987
            // That's still below 0.9999 for p₀=0.9999, but for p₀=0.98 it should be sufficient
            assertThat(ComplianceEvidenceEvaluator.isUndersized(200, 0.98, 0.05)).isFalse();
        }
    }

    @Nested
    @DisplayName("hasComplianceContext()")
    class HasComplianceContext {

        @Test
        @DisplayName("SLA origin with no contract ref has compliance context")
        void slaOriginHasContext() {
            assertThat(ComplianceEvidenceEvaluator.hasComplianceContext("SLA", null)).isTrue();
        }

        @Test
        @DisplayName("SLA origin is case-insensitive")
        void slaOriginCaseInsensitive() {
            assertThat(ComplianceEvidenceEvaluator.hasComplianceContext("sla", null)).isTrue();
        }

        @Test
        @DisplayName("SLO origin has compliance context")
        void sloOriginHasContext() {
            assertThat(ComplianceEvidenceEvaluator.hasComplianceContext("SLO", null)).isTrue();
        }

        @Test
        @DisplayName("POLICY origin has compliance context")
        void policyOriginHasContext() {
            assertThat(ComplianceEvidenceEvaluator.hasComplianceContext("POLICY", null)).isTrue();
        }

        @Test
        @DisplayName("any origin with contract ref has compliance context")
        void contractRefGivesContext() {
            assertThat(ComplianceEvidenceEvaluator.hasComplianceContext("EMPIRICAL", "SLA-2024-001")).isTrue();
        }

        @Test
        @DisplayName("UNSPECIFIED origin with no contract ref has NO compliance context")
        void unspecifiedWithoutContractHasNoContext() {
            assertThat(ComplianceEvidenceEvaluator.hasComplianceContext("UNSPECIFIED", null)).isFalse();
        }

        @Test
        @DisplayName("EMPIRICAL origin with no contract ref has NO compliance context")
        void empiricalWithoutContractHasNoContext() {
            assertThat(ComplianceEvidenceEvaluator.hasComplianceContext("EMPIRICAL", null)).isFalse();
        }

        @Test
        @DisplayName("null origin with no contract ref has NO compliance context")
        void nullOriginWithoutContractHasNoContext() {
            assertThat(ComplianceEvidenceEvaluator.hasComplianceContext(null, null)).isFalse();
        }

        @Test
        @DisplayName("null origin with empty contract ref has NO compliance context")
        void nullOriginWithEmptyContractHasNoContext() {
            assertThat(ComplianceEvidenceEvaluator.hasComplianceContext(null, "")).isFalse();
        }
    }

    @Nested
    @DisplayName("SIZING_NOTE constant")
    class SizingNote {

        @Test
        @DisplayName("contains the exact required phrase")
        void containsExactPhrase() {
            assertThat(ComplianceEvidenceEvaluator.SIZING_NOTE)
                    .isEqualTo("sample not sized for compliance verification");
        }
    }
}
