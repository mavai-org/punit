package org.mavai.punit.api.covariate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CovariateProfile — value semantics and resolution-order preservation")
class CovariateProfileTest {

    @Test
    @DisplayName("empty() returns the canonical empty singleton")
    void emptySingleton() {
        assertThat(CovariateProfile.empty()).isSameAs(CovariateProfile.empty());
        assertThat(CovariateProfile.empty().isEmpty()).isTrue();
        assertThat(CovariateProfile.empty().size()).isZero();
    }

    @Test
    @DisplayName("of(emptyMap) returns the canonical empty singleton")
    void ofEmptyMapReturnsEmpty() {
        assertThat(CovariateProfile.of(Map.of())).isSameAs(CovariateProfile.empty());
    }

    @Test
    @DisplayName("of(map) preserves insertion order")
    void preservesInsertionOrder() {
        LinkedHashMap<String, String> input = new LinkedHashMap<>();
        input.put("zeta", "1");
        input.put("alpha", "2");
        input.put("middle", "3");

        CovariateProfile profile = CovariateProfile.of(input);

        assertThat(profile.values().keySet()).containsExactly("zeta", "alpha", "middle");
    }

    @Test
    @DisplayName("get returns the resolved label")
    void getResolvedLabel() {
        CovariateProfile profile = CovariateProfile.of(Map.of("region", "DE_FR"));
        assertThat(profile.get("region")).contains("DE_FR");
        assertThat(profile.get("missing")).isEmpty();
    }

    @Test
    @DisplayName("returned values map is unmodifiable")
    void valuesUnmodifiable() {
        CovariateProfile profile = CovariateProfile.of(Map.of("a", "1"));
        assertThatThrownBy(() -> profile.values().put("b", "2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("mutating the source map after of() does not mutate the profile")
    void defensivelyCopied() {
        Map<String, String> source = new HashMap<>();
        source.put("a", "1");
        CovariateProfile profile = CovariateProfile.of(source);

        source.put("b", "2");

        assertThat(profile.size()).isEqualTo(1);
        assertThat(profile.get("b")).isEmpty();
    }

    @Test
    @DisplayName("rejects null keys and values")
    void rejectsNulls() {
        Map<String, String> withNullKey = new HashMap<>();
        withNullKey.put(null, "v");
        assertThatThrownBy(() -> CovariateProfile.of(withNullKey))
                .isInstanceOf(NullPointerException.class);

        Map<String, String> withNullValue = new HashMap<>();
        withNullValue.put("k", null);
        assertThatThrownBy(() -> CovariateProfile.of(withNullValue))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("equals is value-based on the values map")
    void valueEquality() {
        CovariateProfile a = CovariateProfile.of(Map.of("k", "v"));
        CovariateProfile b = CovariateProfile.of(Map.of("k", "v"));
        CovariateProfile c = CovariateProfile.of(Map.of("k", "x"));

        assertThat(a).isEqualTo(b).isNotEqualTo(c);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
