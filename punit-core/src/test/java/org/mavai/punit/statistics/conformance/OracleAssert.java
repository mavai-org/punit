package org.mavai.punit.statistics.conformance;

import java.util.Iterator;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.within;

/**
 * Asserts one binding expected field of one fixture case against the
 * oracle, recording the {@code (suite, case, field)} triple <em>before</em>
 * asserting — an attempted-and-failed assertion is a red test, not a
 * coverage gap.
 *
 * <p>Comparison: exact equality for booleans, strings and integer-valued
 * fields, and whenever no tolerance is given; numeric comparison within
 * the suite's tolerance otherwise. An expected {@code null} demands that
 * the production surface produced nothing — a refused configuration has
 * no cutoff, no verdict, no rank, and a framework that returned a number
 * there would fail. Lists and objects (the configuration-error list, the
 * per-criterion rows, the latency constraints) are compared element by
 * element under the same rules.
 */
final class OracleAssert {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OracleAssert() { }

    /** Exact-equality form: booleans, strings, integer-valued fields, lists. */
    static void assertOracle(
            ConformanceRecorder recorder, String suite, JsonNode fixtureCase,
            String field, Object actual) {
        assertOracle(recorder, suite, fixtureCase, field, actual, null);
    }

    static void assertOracle(
            ConformanceRecorder recorder, String suite, JsonNode fixtureCase,
            String field, Object actual, Double tolerance) {
        String caseName = fixtureCase.get("name").asText();
        recorder.record(suite, caseName, field);
        JsonNode expected = fixtureCase.get("expected").get(field);
        String label = suite + "/" + caseName + "/" + field;
        if (expected == null) {
            throw new IllegalStateException(
                    label + ": the fixture case carries no such expected field — check the assertion");
        }
        compare(label, expected, MAPPER.valueToTree(unwrap(actual)), tolerance);
    }

    private static Object unwrap(Object actual) {
        if (actual instanceof OptionalInt oi) {
            return oi.isPresent() ? oi.getAsInt() : null;
        }
        if (actual instanceof OptionalDouble od) {
            return od.isPresent() ? od.getAsDouble() : null;
        }
        if (actual instanceof java.util.Optional<?> o) {
            return o.orElse(null);
        }
        return actual;
    }

    private static void compare(String label, JsonNode expected, JsonNode actual, Double tolerance) {
        if (expected.isNull()) {
            assertThat(actual == null || actual.isNull())
                    .as("%s: the oracle expects no value, but the production surface produced %s",
                            label, actual)
                    .isTrue();
            return;
        }
        if (actual == null || actual.isNull()) {
            fail("%s: the oracle expects %s, but the production surface produced nothing "
                    + "for this binding field", label, expected);
        }
        if (expected.isArray()) {
            assertThat(actual.isArray()).as("%s: expected a list, got %s", label, actual).isTrue();
            assertThat(actual.size()).as("%s: list length (expected %s, got %s)", label, expected, actual)
                    .isEqualTo(expected.size());
            for (int i = 0; i < expected.size(); i++) {
                compare(label + "[" + i + "]", expected.get(i), actual.get(i), tolerance);
            }
        } else if (expected.isObject()) {
            assertThat(actual.isObject()).as("%s: expected an object, got %s", label, actual).isTrue();
            Iterator<Map.Entry<String, JsonNode>> fields = expected.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                compare(label + "." + e.getKey(), e.getValue(), actual.get(e.getKey()), tolerance);
            }
            assertThat(actual.size()).as("%s: fields (expected %s, got %s)", label, expected, actual)
                    .isEqualTo(expected.size());
        } else if (expected.isBoolean()) {
            assertThat(actual.asBoolean()).as(label).isEqualTo(expected.asBoolean());
            assertThat(actual.isBoolean()).as(label + " (type)").isTrue();
        } else if (expected.isTextual()) {
            assertThat(actual.asText()).as(label).isEqualTo(expected.asText());
        } else if (tolerance == null || tolerance == 0.0 || expected.isIntegralNumber() && actual.isIntegralNumber()) {
            if (expected.isIntegralNumber()) {
                assertThat(actual.isNumber()).as(label + " (numeric)").isTrue();
                assertThat(actual.asDouble()).as(label).isEqualTo(expected.asDouble());
            } else {
                assertThat(actual.asDouble()).as(label).isEqualTo(expected.asDouble());
            }
        } else {
            assertThat(actual.asDouble()).as(label).isCloseTo(expected.asDouble(), within(tolerance));
        }
    }
}
