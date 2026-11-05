package org.mavai.punit.internal.engine.explore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mavai.punit.api.FactorBundle;
import org.yaml.snakeyaml.Yaml;

/**
 * The mavai-R exploration-stem conformance case ({@code naming-explore-stem.yaml},
 * fetched from the pinned release's interchange archive by
 * {@code fetchInterchangeCases}): the whole-stem bound, and the stem derived
 * from ordered factors.
 */
class ExploreStemConformanceTest {

    private static final String CASE = "/interchange/naming-explore-stem.yaml";

    @SuppressWarnings("unchecked")
    private static Map<String, Object> conformanceCase() {
        try (InputStream is = ExploreStemConformanceTest.class.getResourceAsStream(CASE)) {
            if (is == null) {
                return fail("Conformance case " + CASE + " is absent from the fetched mavai-R release; "
                        + "it ships in interchange-v0.16.2.zip and later (mavaiRTag)");
            }
            return new Yaml().load(is);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + CASE, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Stream<Map<String, Object>> entries(String part) {
        return ((List<Map<String, Object>>) conformanceCase().get(part)).stream();
    }

    static Stream<Map<String, Object>> boundCases() {
        return entries("bound");
    }

    static Stream<Map<String, Object>> stemCases() {
        return entries("stems");
    }

    @Test
    @DisplayName("the case's bound matches the writer's")
    void boundParametersMatch() {
        Map<String, Object> conformanceCase = conformanceCase();
        assertThat(conformanceCase.get("maxBytes")).isEqualTo(ExploreOutputWriter.MAX_STEM_BYTES);
        assertThat(conformanceCase.get("hashChars")).isEqualTo(8);
    }

    @ParameterizedTest(name = "bound: {0}")
    @MethodSource("boundCases")
    @DisplayName("bounded gives the expected stem")
    void boundedGivesExpectedStem(Map<String, Object> entry) {
        String expected = (String) entry.get("expected");
        assertThat(ExploreOutputWriter.bounded((String) entry.get("stem")))
                .as((String) entry.get("id"))
                .isEqualTo(expected);
        assertThat(expected.getBytes(StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(ExploreOutputWriter.MAX_STEM_BYTES);
    }

    @ParameterizedTest(name = "stems: {0}")
    @MethodSource("stemCases")
    @DisplayName("filenameFor gives the expected stem from ordered factors")
    @SuppressWarnings("unchecked")
    void filenameForGivesExpectedStem(Map<String, Object> entry) {
        Map<String, Object> factors = new LinkedHashMap<>();
        for (Map<String, Object> factor : (List<Map<String, Object>>) entry.get("factors")) {
            factors.put((String) factor.get("name"), factor.get("value"));
        }
        assertThat(new ExploreOutputWriter().filenameFor(FactorBundle.of(factors)))
                .as((String) entry.get("id"))
                .isEqualTo(entry.get("expected"));
    }
}
