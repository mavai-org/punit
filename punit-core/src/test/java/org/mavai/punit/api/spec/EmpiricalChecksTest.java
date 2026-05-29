package org.mavai.punit.api.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EmpiricalChecks")
class EmpiricalChecksTest {

    @Test
    @DisplayName("sampleSizeConstraint returns empty when test ≤ baseline")
    void sampleSizeWithinBound() {
        assertThat(EmpiricalChecks.sampleSizeConstraint("c", 100, 100, Map.of())).isEmpty();
        assertThat(EmpiricalChecks.sampleSizeConstraint("c", 50, 100, Map.of())).isEmpty();
        assertThat(EmpiricalChecks.sampleSizeConstraint("c", 0, 100, Map.of())).isEmpty();
    }

    @Test
    @DisplayName("sampleSizeConstraint returns INCONCLUSIVE when test > baseline")
    void sampleSizeExceeded() {
        Optional<CriterionResult> result = EmpiricalChecks.sampleSizeConstraint(
                "bernoulli-pass-rate", 200, 100, Map.of());

        assertThat(result).isPresent();
        CriterionResult r = result.get();
        assertThat(r.criterionName()).isEqualTo("bernoulli-pass-rate");
        assertThat(r.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(r.explanation())
                .contains("test sample size (200)")
                .contains("baseline sample size (100)")
                .contains("at least as rigorous");
        assertThat(r.detail()).containsEntry("testSampleCount", 200);
        assertThat(r.detail()).containsEntry("baselineSampleCount", 100);
        assertThat(r.detail()).containsEntry("origin", "EMPIRICAL");
        assertThat(r.detail())
                .as("InconclusiveReasons discriminant for the verdict-builder")
                .containsEntry(
                        InconclusiveReasons.DETAIL_KEY,
                        InconclusiveReasons.BASELINE_SAMPLE_SIZE_EXCEEDED);
    }

    @Test
    @DisplayName("additionalDetail entries are merged into the violation's detail map")
    void additionalDetailMerged() {
        Optional<CriterionResult> result = EmpiricalChecks.sampleSizeConstraint(
                "percentile-latency", 1000, 100,
                Map.of("assertedPercentiles", "p95,p99"));

        assertThat(result).isPresent();
        Map<String, Object> detail = result.get().detail();
        assertThat(detail).containsEntry("assertedPercentiles", "p95,p99");
        assertThat(detail).containsEntry("testSampleCount", 1000);
        assertThat(detail).containsEntry("baselineSampleCount", 100);
        assertThat(detail).containsEntry("origin", "EMPIRICAL");
    }

    @Test
    @DisplayName("additionalDetail's origin is preserved if the caller already set it")
    void additionalDetailOriginIsPreserved() {
        Optional<CriterionResult> result = EmpiricalChecks.sampleSizeConstraint(
                "c", 200, 100, Map.of("origin", "CUSTOM"));

        assertThat(result.get().detail()).containsEntry("origin", "CUSTOM");
    }

    @Test
    @DisplayName("rejects null arguments")
    void rejectsNulls() {
        assertThatNullPointerException()
                .isThrownBy(() -> EmpiricalChecks.sampleSizeConstraint(null, 1, 1, Map.of()));
        assertThatNullPointerException()
                .isThrownBy(() -> EmpiricalChecks.sampleSizeConstraint("c", 1, 1, null));
    }

    // ── inputsIdentityMatch ──────────────────────────────────────────

    @Test
    @DisplayName("inputsIdentityMatch returns empty when identities are equal")
    void inputsIdentityMatching() {
        assertThat(EmpiricalChecks.inputsIdentityMatch(
                "c", "sha256:abc", "sha256:abc", Map.of())).isEmpty();
    }

    @Test
    @DisplayName("inputsIdentityMatch returns INCONCLUSIVE when identities differ")
    void inputsIdentityMismatch() {
        Optional<CriterionResult> result = EmpiricalChecks.inputsIdentityMatch(
                "bernoulli-pass-rate", "sha256:test", "sha256:baseline", Map.of());

        assertThat(result).isPresent();
        CriterionResult r = result.get();
        assertThat(r.criterionName()).isEqualTo("bernoulli-pass-rate");
        assertThat(r.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(r.explanation())
                .contains("inputs identity")
                .contains("sha256:test")
                .contains("sha256:baseline")
                .contains("re-run the baseline measure");
        assertThat(r.detail()).containsEntry("testInputsIdentity", "sha256:test");
        assertThat(r.detail()).containsEntry("baselineInputsIdentity", "sha256:baseline");
        assertThat(r.detail()).containsEntry("origin", "EMPIRICAL");
        assertThat(r.detail())
                .as("InconclusiveReasons discriminant for the verdict-builder")
                .containsEntry(
                        InconclusiveReasons.DETAIL_KEY,
                        InconclusiveReasons.BASELINE_INPUTS_MISMATCH);
    }

    @Test
    @DisplayName("inputsIdentityMatch truncates long sha256 identities in the explanation")
    void inputsIdentityTruncatesLongHashes() {
        String longTestIdentity = "sha256:abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
        String longBaselineIdentity = "sha256:fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";

        CriterionResult result = EmpiricalChecks.inputsIdentityMatch(
                "c", longTestIdentity, longBaselineIdentity, Map.of()).orElseThrow();

        // Explanation contains the truncated forms (12 hex chars), not the full hashes.
        assertThat(result.explanation())
                .contains("sha256:abcdef012345…")
                .contains("sha256:fedcba987654…")
                .doesNotContain(longTestIdentity)
                .doesNotContain(longBaselineIdentity);
        // Full identities still surface in the detail map for downstream reporting.
        assertThat(result.detail()).containsEntry("testInputsIdentity", longTestIdentity);
        assertThat(result.detail()).containsEntry("baselineInputsIdentity", longBaselineIdentity);
    }

    @Test
    @DisplayName("inputsIdentityMatch additionalDetail entries are merged into the violation's detail map")
    void inputsIdentityAdditionalDetailMerged() {
        Optional<CriterionResult> result = EmpiricalChecks.inputsIdentityMatch(
                "percentile-latency", "sha256:a", "sha256:b",
                Map.of("assertedPercentiles", "p95,p99"));

        Map<String, Object> detail = result.orElseThrow().detail();
        assertThat(detail).containsEntry("assertedPercentiles", "p95,p99");
    }

    @Test
    @DisplayName("inputsIdentityMatch rejects null arguments")
    void inputsIdentityRejectsNulls() {
        assertThatNullPointerException()
                .isThrownBy(() -> EmpiricalChecks.inputsIdentityMatch(null, "a", "b", Map.of()));
        assertThatNullPointerException()
                .isThrownBy(() -> EmpiricalChecks.inputsIdentityMatch("c", null, "b", Map.of()));
        assertThatNullPointerException()
                .isThrownBy(() -> EmpiricalChecks.inputsIdentityMatch("c", "a", null, Map.of()));
        assertThatNullPointerException()
                .isThrownBy(() -> EmpiricalChecks.inputsIdentityMatch("c", "a", "b", null));
    }
}
