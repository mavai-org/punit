package org.mavai.punit.lm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mavai.punit.decl.spi.ServiceType;

/**
 * The lenient tier: an explore grid may span providers with differing
 * capability support, so a point drops what its provider cannot honour
 * and states it — the configuration that actually runs, and its
 * covariates, carry only what was honoured.
 */
@DisplayName("language-model explore points")
class ExplorePointTest {

    private final ServiceType type = new LanguageModelServiceType();

    @BeforeEach
    void credential() {
        System.setProperty("mavai.llm.api-key", "test-key");
    }

    @AfterEach
    void clear() {
        System.clearProperty("mavai.llm.api-key");
    }

    private Map<String, Object> configuration(Object... pairs) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("system-prompt", "You answer politely.");
        record.put("model", "test-model");
        for (int i = 0; i < pairs.length; i += 2) {
            record.put((String) pairs[i], pairs[i + 1]);
        }
        return record;
    }

    @Test
    @DisplayName("a schema the point's provider withholds is dropped with a note")
    void schemaDegrades() {
        ServiceType.ExplorePoint point = type.explorePoint("svc", configuration(
                "provider", "apertus", "response-schema", Map.of("type", "object")));
        assertThat(point.note())
                .contains("no structured-output support")
                .contains("not sent for this configuration");
        assertThat(point.service().configurationCovariates())
                .doesNotContainKey("responseSchemaFingerprint");
    }

    @Test
    @DisplayName("several unhonoured keys degrade together, the notes joined")
    void severalDegradations() {
        ServiceType.ExplorePoint point = type.explorePoint("svc", configuration(
                "provider", "apertus",
                "response-schema", Map.of("type", "object"),
                "prompt-caching", true));
        assertThat(point.note())
                .contains("no structured-output support")
                .contains("no prompt-caching support");
        assertThat(point.service().configurationCovariates())
                .doesNotContainKeys("responseSchemaFingerprint", "promptCaching");
    }

    @Test
    @DisplayName("a point whose provider honours everything runs undegraded, no note")
    void honouredPointHasNoNote() {
        ServiceType.ExplorePoint point = type.explorePoint("svc", configuration(
                "provider", "anthropic", "thinking", "adaptive", "prompt-caching", true));
        assertThat(point.note()).isNull();
        assertThat(point.service().configurationCovariates())
                .containsEntry("thinking", "adaptive")
                .containsEntry("promptCaching", "true");
    }

    @Test
    @DisplayName("the vendor veto binds the lenient tier too")
    void vetoStillBinds() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                type.explorePoint("svc", configuration(
                        "provider", "anthropic", "thinking", "adaptive", "temperature", 0.5)))
                .isInstanceOf(org.mavai.punit.decl.ContractConfigurationException.class)
                .hasMessageContaining("constrains sampling parameters");
    }
}
