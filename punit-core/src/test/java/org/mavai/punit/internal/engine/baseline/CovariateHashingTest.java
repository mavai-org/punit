package org.mavai.punit.internal.engine.baseline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mavai.punit.api.covariate.CovariateProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CovariateHashing — per-covariate-value 4-char hashes")
class CovariateHashingTest {

    @Test
    @DisplayName("empty profile produces an empty list")
    void empty() {
        assertThat(CovariateHashing.hashesFor(CovariateProfile.empty())).isEmpty();
    }

    @Test
    @DisplayName("hashes are exactly 4 lowercase hex characters per covariate")
    void shapeAndLength() {
        Map<String, String> profile = new LinkedHashMap<>();
        profile.put("day_of_week", "WEEKDAY");
        profile.put("region", "DE_FR");
        profile.put("model_version", "claude-opus-4-7");

        List<String> hashes = CovariateHashing.hashesFor(CovariateProfile.of(profile));

        assertThat(hashes).hasSize(3);
        assertThat(hashes).allSatisfy(h -> {
            assertThat(h).hasSize(4);
            assertThat(h).matches("^[0-9a-f]{4}$");
        });
    }

    @Test
    @DisplayName("hashes appear in declaration (insertion) order")
    void preservesOrder() {
        Map<String, String> a = new LinkedHashMap<>();
        a.put("first", "alpha");
        a.put("second", "beta");

        Map<String, String> b = new LinkedHashMap<>();
        b.put("second", "beta");
        b.put("first", "alpha");

        List<String> hashesA = CovariateHashing.hashesFor(CovariateProfile.of(a));
        List<String> hashesB = CovariateHashing.hashesFor(CovariateProfile.of(b));

        // Same covariates, same values — but different declaration
        // order produces different filename ordering. The hashes are
        // the same set; the sequence differs.
        assertThat(hashesA).containsExactlyInAnyOrderElementsOf(hashesB);
        assertThat(hashesA).isNotEqualTo(hashesB);
    }

    @Test
    @DisplayName("hash is deterministic — same (key, value) always produces the same hash")
    void deterministic() {
        String h1 = CovariateHashing.hashOne("region", "DE_FR");
        String h2 = CovariateHashing.hashOne("region", "DE_FR");
        assertThat(h1).isEqualTo(h2);
    }

    @Test
    @DisplayName("changing key or value changes the hash")
    void sensitiveToInputs() {
        String base = CovariateHashing.hashOne("region", "DE_FR");
        String differentKey = CovariateHashing.hashOne("Region", "DE_FR");
        String differentValue = CovariateHashing.hashOne("region", "DE_GB");

        assertThat(differentKey).isNotEqualTo(base);
        assertThat(differentValue).isNotEqualTo(base);
    }

    @Test
    @DisplayName("hash matches first-4 of SHA-256 over UTF-8 of 'key=value'")
    void matchesNormativeAlgorithm() {
        // Lock in the algorithm against an externally-computable
        // value. SHA-256("region=DE_FR") = the well-known digest;
        // first 4 hex chars are stable across platforms.
        String input = "region=DE_FR";
        String full = org.mavai.punit.internal.util.HashUtils.sha256(input);
        String expected = full.substring(0, 4);

        assertThat(CovariateHashing.hashOne("region", "DE_FR"))
                .isEqualTo(expected);
    }
}
