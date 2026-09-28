package org.mavai.punit.statistics.conformance;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.mavai.punit.statistics.ConfigurationError;
import org.mavai.punit.statistics.DecisionRule;
import org.mavai.punit.statistics.Methodology;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fixtures name the methodology, the decision rules and the
 * configuration errors they encode; these must be the ones this package
 * implements — read from the manifest, never a literal.
 */
@DisplayName("Methodology conformance (mavai-R manifest)")
class MethodologyConformanceTest {

    @Test
    @DisplayName("the manifest names the methodology, rules and configuration errors punit implements")
    void manifestNamesWhatPunitImplements() {
        JsonNode manifest = ConformanceFixtures.load("manifest.json");
        assertThat(manifest.get("methodologyVersion").asText()).isEqualTo(Methodology.VERSION);
        Set<String> rules = new HashSet<>();
        manifest.get("decisionRules").forEach(r ->
                rules.add(r.get("id").asText() + "@" + r.get("version").asInt()));
        assertThat(rules).isEqualTo(Arrays.stream(DecisionRule.values())
                .map(r -> r.id() + "@" + r.version())
                .collect(Collectors.toSet()));
        List<String> errors = new java.util.ArrayList<>();
        manifest.get("configurationErrors").forEach(e -> errors.add(e.asText()));
        assertThat(errors).as("the fixed reporting order")
                .containsExactlyElementsOf(Arrays.stream(ConfigurationError.values())
                        .map(Enum::name).toList());
    }

    @Test
    @DisplayName("every in-scope suite declares this methodology and only known rules")
    void suitesDeclareThisMethodology() {
        ConformanceLedger ledger = ConformanceLedger.load();
        JsonNode manifest = ConformanceFixtures.load("manifest.json");
        for (String suite : ledger.inScopeSuites()) {
            JsonNode file = ConformanceFixtures.load(
                    manifest.get("suites").get(suite).get("file").asText());
            assertThat(file.get("methodologyVersion").asText()).as(suite).isEqualTo(Methodology.VERSION);
            file.get("decisionRules").forEach(r ->
                    assertThat(DecisionRule.fromId(r.get("id").asText())).as(suite).isPresent());
            for (JsonNode c : file.get("cases")) {
                JsonNode rule = c.get("decisionRule");
                if (rule == null) {
                    continue;
                }
                List<JsonNode> ids = new java.util.ArrayList<>();
                if (rule.isArray()) {
                    rule.forEach(ids::add);
                } else {
                    ids.add(rule);
                }
                ids.forEach(id -> assertThat(DecisionRule.fromId(id.asText()))
                        .as(suite + "/" + c.get("name").asText()).isPresent());
            }
        }
    }
}
