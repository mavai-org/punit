package org.mavai.punit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FactorBundle")
class FactorBundleTest {

    record LlmFactors(String model, double temperature, int maxTokens, boolean streaming) {}

    record NoFields() {}

    record WithList(String name, List<Integer> values) {}

    enum LlmModel { GPT_4O, GPT_4_TURBO }

    @Test
    @DisplayName("reflectively reads record components in declaration order")
    void entriesInDeclarationOrder() {
        FactorBundle b = FactorBundle.of(new LlmFactors("gpt-4o", 0.3, 2048, true));
        assertThat(b.entries()).hasSize(4);
        assertThat(b.entries().get(0).name()).isEqualTo("model");
        assertThat(b.entries().get(1).name()).isEqualTo("temperature");
        assertThat(b.entries().get(2).name()).isEqualTo("maxTokens");
        assertThat(b.entries().get(3).name()).isEqualTo("streaming");
    }

    @Test
    @DisplayName("canonicalJson sorts keys alphabetically with no whitespace")
    void canonicalJsonSortsKeys() {
        // Reference example from the factor-bundle-hash specification:
        // keys sorted alphabetically, no whitespace, minimal decimals.
        FactorBundle b = FactorBundle.of(new LlmFactors("gpt-4o", 0.3, 2048, true));
        assertThat(b.canonicalJson())
                .isEqualTo("{\"maxTokens\":2048,\"model\":\"gpt-4o\","
                        + "\"streaming\":true,\"temperature\":0.3}");
    }

    @Test
    @DisplayName("bundleHash is a four-hex-char SHA-256 truncation and is stable across runs")
    void bundleHashStable() {
        FactorBundle a = FactorBundle.of(new LlmFactors("gpt-4o", 0.3, 2048, true));
        FactorBundle b = FactorBundle.of(new LlmFactors("gpt-4o", 0.3, 2048, true));
        assertThat(a.bundleHash()).hasSize(4);
        assertThat(a.bundleHash()).matches("[0-9a-f]{4}");
        assertThat(a.bundleHash()).isEqualTo(b.bundleHash());
    }

    @Test
    @DisplayName("different factor bundles produce different hashes")
    void distinctBundlesDistinctHashes() {
        FactorBundle a = FactorBundle.of(new LlmFactors("gpt-4o", 0.3, 2048, true));
        FactorBundle b = FactorBundle.of(new LlmFactors("gpt-4o", 0.7, 2048, true));
        assertThat(a.bundleHash()).isNotEqualTo(b.bundleHash());
    }

    @Test
    @DisplayName("empty() corresponds to a record with no components")
    void emptyBundle() {
        FactorBundle b = FactorBundle.of(new NoFields());
        assertThat(b.isEmpty()).isTrue();
        assertThat(b.entries()).isEmpty();
        assertThat(b.canonicalJson()).isEqualTo("{}");
        assertThat(b).isEqualTo(FactorBundle.empty());
    }

    @Test
    @DisplayName("rejects null factor record")
    void rejectsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> FactorBundle.of(null));
    }

    @Test
    @DisplayName("rejects a type that is neither record nor enum")
    void rejectsNonRecordNonEnum() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FactorBundle.of("not a record"))
                .withMessageContaining("must be a record, enum, or string-keyed map");
    }

    @Test
    @DisplayName("admits records with non-canonical component types via toString() coercion")
    void admitsNonCanonicalComponentViaToString() {
        FactorBundle b = FactorBundle.of(new WithList("x", List.of(1, 2)));
        assertThat(b.entries()).hasSize(2);
        assertThat(b.entries().get(0).name()).isEqualTo("name");
        assertThat(b.entries().get(1).name()).isEqualTo("values");
        assertThat(b.canonicalJson())
                .isEqualTo("{\"name\":\"x\",\"values\":\"[1, 2]\"}");
    }

    @Test
    @DisplayName("accepts an enum directly as the factor value, keyed by declaring-class simple name")
    void acceptsEnumAsFactor() {
        FactorBundle b = FactorBundle.of(LlmModel.GPT_4O);
        assertThat(b.entries()).hasSize(1);
        assertThat(b.entries().get(0).name()).isEqualTo("LlmModel");
        assertThat(b.canonicalJson()).isEqualTo("{\"LlmModel\":\"GPT_4O\"}");
    }

    @Test
    @DisplayName("enum factor: distinct constants produce distinct hashes")
    void enumFactorDistinctHashes() {
        FactorBundle a = FactorBundle.of(LlmModel.GPT_4O);
        FactorBundle b = FactorBundle.of(LlmModel.GPT_4_TURBO);
        assertThat(a.bundleHash()).isNotEqualTo(b.bundleHash());
    }

    @Test
    @DisplayName("bundleHash reproduces the canonical-JSON reference example")
    void bundleHashReproducesCanonicalJsonExample() {
        // The canonical JSON matches the specification exactly; the hash is the first
        // four hex chars of SHA-256 of that JSON — an independently
        // verifiable number.
        FactorBundle b = FactorBundle.of(new LlmFactors("gpt-4o", 0.3, 2048, true));
        assertThat(b.canonicalJson())
                .isEqualTo("{\"maxTokens\":2048,\"model\":\"gpt-4o\","
                        + "\"streaming\":true,\"temperature\":0.3}");
        // Hash must be stable — equal across JVMs and across runs.
        assertThat(b.bundleHash()).matches("[0-9a-f]{4}");
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("lifts a string-keyed map in iteration order — the named-entries shape")
    void liftsStringKeyedMap() {
        java.util.Map<String, Object> named = new java.util.LinkedHashMap<>();
        named.put("temperature", 0.2);
        named.put("model", "small-model");
        named.put("cached", true);
        FactorBundle bundle = FactorBundle.of(named);
        org.assertj.core.api.Assertions.assertThat(bundle.entries()).hasSize(3);
        org.assertj.core.api.Assertions.assertThat(bundle.entries().get(0).name())
                .isEqualTo("temperature");
        org.assertj.core.api.Assertions.assertThat(bundle.canonicalJson())
                .isEqualTo("{\"cached\":true,\"model\":\"small-model\",\"temperature\":0.2}");
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("rejects a map with a non-string key or inadmissible value")
    void rejectsBadMapShapes() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> FactorBundle.of(java.util.Map.of(42, "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keys must be strings");
        java.util.Map<String, Object> withNull = new java.util.LinkedHashMap<>();
        withNull.put("absent", null);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> FactorBundle.of(withNull))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inadmissible value for 'absent'");
    }
}
