package org.mavai.punit.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ComplianceEvidenceEvaluator}.
 *
 * <p>The evaluator uses a Wilson score one-sided lower confidence bound to determine
 * whether a sample size is sufficient for compliance-grade evidence. With the default
 * α=0.001 (z≈3.09), the lower bound for a perfect observation (zero failures)
 * is: n / (n + z²) ≈ n / (n + 9.55).
 *
 * <p>For target p₀=0.9999:
 * <ul>
 *   <li>n=200: lower bound ≈ 0.954 &lt; 0.9999 → undersized</li>
 *   <li>n=500: lower bound ≈ 0.981 &lt; 0.9999 → undersized</li>
 *   <li>n=10,000: lower bound ≈ 0.999 &lt; 0.9999 → undersized</li>
 *   <li>n≈95,500 needed for lower bound to reach 0.9999</li>
 * </ul>
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
        @DisplayName("p0=0.9999 with N=10000 is still undersized — lower bound ≈ 0.999 < 0.9999")
        void undersizedAt10000SamplesFor9999() {
            // With α=0.001, even n=10,000 is insufficient for p₀=0.9999.
            // The Wilson lower bound at n=10,000 is approximately n/(n+z²) ≈ 10000/10009.55 ≈ 0.99905,
            // which is below 0.9999. You would need approximately n ≈ 95,500 samples.
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
