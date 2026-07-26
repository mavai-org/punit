package org.mavai.punit.decl.internal.path;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Drives the official JSONPath compliance test suite (the
 * jsonpath-standard project's {@code cts.json}, vendored under
 * {@code src/test/resources/jsonpath-cts/}) against punit-decl's own
 * engine.
 *
 * <p>The known-failures list below is the deliberate, visible record of
 * accepted non-compliance — the family's format specification makes
 * suite conformance a SHOULD, and the owner's ruling accepts less than
 * total compliance. The list is asserted in both directions: a test
 * failing outside the list fails the build (a regression), and a listed
 * test that starts passing fails the build too (the list must shrink,
 * never silently overstate).
 */
@DisplayName("JSONPath engine compliance (RFC 9535 suite)")
class JsonPathComplianceTest {

    /** Suite cases the engine is known not to satisfy yet. */
    private static final Set<String> KNOWN_FAILURES = Set.of();

    @Test
    @DisplayName("every suite case passes, except the known-failures list, exactly")
    void complianceSuite() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<?, ?> suite;
        try (InputStream cts = getClass().getResourceAsStream("/jsonpath-cts/cts.json")) {
            suite = mapper.readValue(cts, Map.class);
        }
        List<?> tests = (List<?>) suite.get("tests");
        List<String> unexpectedFailures = new ArrayList<>();
        List<String> unexpectedPasses = new ArrayList<>();
        for (Object entry : tests) {
            Map<?, ?> test = (Map<?, ?>) entry;
            String name = (String) test.get("name");
            boolean passed = runs(test);
            if (!passed && !KNOWN_FAILURES.contains(name)) {
                unexpectedFailures.add(name + "  [" + test.get("selector") + "]");
            }
            if (passed && KNOWN_FAILURES.contains(name)) {
                unexpectedPasses.add(name);
            }
        }
        assertThat(unexpectedFailures)
                .as("suite cases failing outside the known-failures list")
                .isEmpty();
        assertThat(unexpectedPasses)
                .as("known-failures entries that now pass — prune the list")
                .isEmpty();
    }

    private boolean runs(Map<?, ?> test) {
        String selector = (String) test.get("selector");
        boolean invalid = Boolean.TRUE.equals(test.get("invalid_selector"));
        if (invalid) {
            try {
                CompiledJsonPath.compile(selector);
                return false;
            } catch (PathSyntaxException expected) {
                return true;
            }
        }
        List<Object> actual;
        try {
            actual = CompiledJsonPath.compile(selector).select(test.get("document"));
        } catch (RuntimeException error) {
            return false;
        }
        if (test.containsKey("result")) {
            return resultMatches((List<?>) test.get("result"), actual);
        }
        List<?> alternatives = (List<?>) test.get("results");
        for (Object alternative : alternatives) {
            if (resultMatches((List<?>) alternative, actual)) {
                return true;
            }
        }
        return false;
    }

    private boolean resultMatches(List<?> expected, List<Object> actual) {
        return Objects.equals(expected, actual);
    }
}
