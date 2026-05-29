package org.mavai.punit.internal.engine.baseline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.mavai.punit.api.FactorBundle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FactorsFingerprint")
class FactorsFingerprintTest {

    record Factors(String model, double temperature) { }

    @Test
    @DisplayName("8-char hex for any FactorBundle")
    void hexLength() {
        String fingerprint = FactorsFingerprint.of(
                FactorBundle.of(new Factors("gpt-4o", 0.0)));

        assertThat(fingerprint).hasSize(8).matches("[0-9a-f]{8}");
    }

    @Test
    @DisplayName("deterministic — equal bundles produce equal fingerprints")
    void deterministic() {
        String a = FactorsFingerprint.of(FactorBundle.of(new Factors("gpt-4o", 0.0)));
        String b = FactorsFingerprint.of(FactorBundle.of(new Factors("gpt-4o", 0.0)));

        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("differs when any factor field differs")
    void contentSensitivity() {
        String a = FactorsFingerprint.of(FactorBundle.of(new Factors("gpt-4o", 0.0)));
        String b = FactorsFingerprint.of(FactorBundle.of(new Factors("gpt-4o", 0.1)));
        String c = FactorsFingerprint.of(FactorBundle.of(new Factors("gpt-3.5", 0.0)));

        assertThat(a).isNotEqualTo(b).isNotEqualTo(c);
        assertThat(b).isNotEqualTo(c);
    }

    @Test
    @DisplayName("empty FactorBundle has a stable fingerprint distinct from any non-empty one")
    void emptyBundle() {
        String empty = FactorsFingerprint.of(FactorBundle.empty());
        String nonEmpty = FactorsFingerprint.of(FactorBundle.of(new Factors("anything", 1.0)));

        assertThat(empty).hasSize(8).matches("[0-9a-f]{8}");
        assertThat(empty).isNotEqualTo(nonEmpty);
    }

    @Test
    @DisplayName("rejects null bundle")
    void rejectsNullBundle() {
        assertThatNullPointerException().isThrownBy(() -> FactorsFingerprint.of(null));
    }
}
