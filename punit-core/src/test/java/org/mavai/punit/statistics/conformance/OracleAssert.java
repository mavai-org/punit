package org.mavai.punit.statistics.conformance;

import com.fasterxml.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.within;

/**
 * Asserts one binding expected field of one fixture case against the
 * oracle, recording the {@code (suite, case, field)} triple <em>before</em>
 * asserting — an attempted-and-failed assertion is a red test, not a
 * coverage gap. A {@code null} actual value fails with a
 * missing-capability diagnostic: the production surface produced nothing
 * for a field the manifest classifies as binding.
 */
final class OracleAssert {

    private OracleAssert() { }

    /** Exact-equality form: booleans, strings, integer-valued fields. */
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
        if (actual == null) {
            fail("%s: the oracle expects %s, but the production surface produced nothing "
                    + "for this binding field", label, expected);
        }
        if (expected.isBoolean()) {
            assertThat(actual).as(label).isEqualTo(expected.asBoolean());
        } else if (expected.isTextual()) {
            assertThat(String.valueOf(actual)).as(label).isEqualTo(expected.asText());
        } else if (tolerance == null || tolerance == 0.0) {
            if (expected.isIntegralNumber()) {
                assertThat(((Number) actual).longValue()).as(label).isEqualTo(expected.asLong());
            } else {
                assertThat(((Number) actual).doubleValue()).as(label).isEqualTo(expected.asDouble());
            }
        } else {
            assertThat(((Number) actual).doubleValue())
                    .as(label)
                    .isCloseTo(expected.asDouble(), within(tolerance));
        }
    }
}
