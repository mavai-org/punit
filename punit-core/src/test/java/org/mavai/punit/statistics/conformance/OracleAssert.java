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
    /**
     * Discharge a binding field whose expected value on this case is null.
     *
     * <p>A refusal case carries every numeric expectation as null, because
     * none exists: the design was declined, so there is no sample size, no
     * floor, no power. The manifest still lists those fields as binding —
     * it classifies fields per suite, not per case — so the obligation is
     * real and has to be met by saying something true about them.
     *
     * <p>What is true is that the oracle expects no value and the production
     * surface produced none. Asserting that is not a formality: a framework
     * that returned a number here — having clamped the baseline to something
     * small, or fallen back to a fixed-threshold form — would fail it, which
     * is exactly the repair companion §4.3.4 forbids.
     */
    static void assertOracleAbsent(
            ConformanceRecorder recorder, String suite, JsonNode fixtureCase, String field) {
        String caseName = fixtureCase.get("name").asText();
        recorder.record(suite, caseName, field);
        JsonNode expected = fixtureCase.get("expected").get(field);
        String label = suite + "/" + caseName + "/" + field;
        if (expected == null) {
            throw new IllegalStateException(
                    label + ": the fixture case carries no such expected field — check the assertion");
        }
        assertThat(expected.isNull())
                .as("%s: expected an absent value, but the oracle publishes %s", label, expected)
                .isTrue();
    }

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
