package org.mavai.punit.api.covariate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CovariateAlignment — observed-vs-baseline structural alignment")
class CovariateAlignmentTest {

    @Test
    @DisplayName("none() represents the no-covariates-declared state")
    void noneCanonical() {
        CovariateAlignment alignment = CovariateAlignment.none();

        assertThat(alignment.observed().isEmpty()).isTrue();
        assertThat(alignment.baseline().isEmpty()).isTrue();
        assertThat(alignment.aligned()).isTrue();
        assertThat(alignment.mismatches()).isEmpty();
    }

    @Test
    @DisplayName("identical profiles produce aligned = true and no mismatches")
    void identicalProfiles() {
        CovariateProfile profile = CovariateProfile.of(new LinkedHashMap<>(Map.of(
                "region", "EU",
                "model_version", "v1")));

        CovariateAlignment alignment = CovariateAlignment.compute(profile, profile);

        assertThat(alignment.aligned()).isTrue();
        assertThat(alignment.mismatches()).isEmpty();
        assertThat(alignment.observed()).isEqualTo(profile);
        assertThat(alignment.baseline()).isEqualTo(profile);
    }

    @Test
    @DisplayName("differing values on the same key produce a mismatch")
    void differingValues() {
        CovariateProfile observed = CovariateProfile.of(Map.of("region", "APAC"));
        CovariateProfile baseline = CovariateProfile.of(Map.of("region", "EU"));

        CovariateAlignment alignment = CovariateAlignment.compute(observed, baseline);

        assertThat(alignment.aligned()).isFalse();
        assertThat(alignment.mismatches())
                .singleElement()
                .satisfies(m -> {
                    assertThat(m.covariateKey()).isEqualTo("region");
                    assertThat(m.observed()).isEqualTo("APAC");
                    assertThat(m.baseline()).isEqualTo("EU");
                });
    }

    @Test
    @DisplayName("baseline-only key produces a mismatch with observed=null")
    void baselineOnlyKey() {
        CovariateProfile observed = CovariateProfile.empty();
        CovariateProfile baseline = CovariateProfile.of(Map.of("region", "EU"));

        CovariateAlignment alignment = CovariateAlignment.compute(observed, baseline);

        assertThat(alignment.aligned()).isFalse();
        assertThat(alignment.mismatches())
                .singleElement()
                .satisfies(m -> {
                    assertThat(m.covariateKey()).isEqualTo("region");
                    assertThat(m.observed()).isNull();
                    assertThat(m.baseline()).isEqualTo("EU");
                });
    }

    @Test
    @DisplayName("observed-only key (baseline lacks it) produces a mismatch with baseline=null")
    void observedOnlyKey() {
        CovariateProfile observed = CovariateProfile.of(new LinkedHashMap<>(Map.of(
                "region", "EU",
                "model_version", "v1")));
        CovariateProfile baseline = CovariateProfile.of(Map.of("region", "EU"));

        CovariateAlignment alignment = CovariateAlignment.compute(observed, baseline);

        assertThat(alignment.aligned()).isFalse();
        assertThat(alignment.mismatches())
                .singleElement()
                .satisfies(m -> {
                    assertThat(m.covariateKey()).isEqualTo("model_version");
                    assertThat(m.observed()).isEqualTo("v1");
                    assertThat(m.baseline()).isNull();
                });
    }

    @Test
    @DisplayName("empty baseline (no matched baseline) produces aligned = true regardless of observed")
    void emptyBaselineAligns() {
        CovariateProfile observed = CovariateProfile.of(Map.of("region", "EU"));

        CovariateAlignment alignment = CovariateAlignment.compute(
                observed, CovariateProfile.empty());

        assertThat(alignment.aligned()).isTrue();
        assertThat(alignment.mismatches()).isEmpty();
        assertThat(alignment.observed()).isEqualTo(observed);
        assertThat(alignment.baseline().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("multiple mismatches list every differing key")
    void multipleMismatches() {
        CovariateProfile observed = CovariateProfile.of(new LinkedHashMap<>(Map.of(
                "region", "APAC",
                "model_version", "v2",
                "feature_flag", "on")));
        CovariateProfile baseline = CovariateProfile.of(new LinkedHashMap<>(Map.of(
                "region", "EU",
                "model_version", "v1",
                "feature_flag", "on")));

        CovariateAlignment alignment = CovariateAlignment.compute(observed, baseline);

        assertThat(alignment.aligned()).isFalse();
        assertThat(alignment.mismatches()).hasSize(2);
        assertThat(alignment.mismatches())
                .extracting(CovariateAlignment.Mismatch::covariateKey)
                .containsExactlyInAnyOrder("region", "model_version");
    }
}
