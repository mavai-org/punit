package org.mavai.punit.sentinel.verdict;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.mavai.punit.api.spec.Verdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdictBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WebhookVerdictSinkTest {

    private static ProbabilisticTestVerdict sampleVerdict() {
        return new ProbabilisticTestVerdictBuilder()
                .correlationId("v:abc123")
                .identity("TestClass", "testMethod", "serviceContract1")
                .execution(100, 100, 95, 5, 0.9, 0.95, 1000)
                .environmentMetadata(Map.of("environment", "staging"))
                .junitPassed(true)
                .criterionVerdict(Verdict.PASS)
                .build();
    }

    @Nested
    @DisplayName("builder")
    class BuilderTests {

        @Test
        @DisplayName("builds with minimal configuration")
        void buildsWithMinimalConfig() {
            var sink = WebhookVerdictSink.builder("https://example.com/webhook").build();

            assertThat(sink).isNotNull();
        }

        @Test
        @DisplayName("accepts custom timeout")
        void acceptsCustomTimeout() {
            var sink = WebhookVerdictSink.builder("https://example.com/webhook")
                    .timeout(Duration.ofSeconds(5))
                    .build();

            assertThat(sink).isNotNull();
        }

        @Test
        @DisplayName("accepts custom headers")
        void acceptsCustomHeaders() {
            var sink = WebhookVerdictSink.builder("https://example.com/webhook")
                    .header("Authorization", "Bearer token123")
                    .header("X-Custom", "value")
                    .build();

            assertThat(sink).isNotNull();
        }
    }

    @Nested
    @DisplayName("toJson()")
    class ToJson {

        @Test
        @DisplayName("produces valid JSON with all fields")
        void producesValidJsonWithAllFields() {
            var verdict = sampleVerdict();
            String json = WebhookVerdictSink.toJson(verdict);

            assertThat(json).contains("\"correlationId\":\"v:abc123\"");
            assertThat(json).contains("\"testName\":\"TestClass.testMethod\"");
            assertThat(json).contains("\"serviceContractId\":\"serviceContract1\"");
            assertThat(json).contains("\"passed\":true");
            assertThat(json).contains("\"punitVerdict\":\"PASS\"");
        }

        @Test
        @DisplayName("serialises environment metadata")
        void serialisesEnvironmentMetadata() {
            var verdict = sampleVerdict();
            String json = WebhookVerdictSink.toJson(verdict);

            assertThat(json).contains("\"environmentMetadata\":{");
            assertThat(json).contains("\"environment\":\"staging\"");
        }

        @Test
        @DisplayName("serialises false passed value")
        void serialisesFalsePassedValue() {
            var verdict = new ProbabilisticTestVerdictBuilder()
                    .correlationId("v:fail01")
                    .identity("TestClass", "failingTest", "uc2")
                    .execution(100, 100, 80, 20, 0.9, 0.8, 1000)
                    .junitPassed(false)
                    .criterionVerdict(Verdict.FAIL)
                    .build();
            String json = WebhookVerdictSink.toJson(verdict);

            assertThat(json).contains("\"passed\":false");
        }

        @Test
        @DisplayName("escapes quotes in string values")
        void escapesQuotesInValues() {
            var verdict = new ProbabilisticTestVerdictBuilder()
                    .correlationId("v:esc001")
                    .identity("test\"Class", "test\"Method", "use\"Case")
                    .execution(100, 100, 95, 5, 0.9, 0.95, 1000)
                    .junitPassed(true)
                    .criterionVerdict(Verdict.PASS)
                    .build();
            String json = WebhookVerdictSink.toJson(verdict);

            assertThat(json).contains("\"testName\":\"test\\\"Class.test\\\"Method\"");
            assertThat(json).contains("\"serviceContractId\":\"use\\\"Case\"");
        }

        @Test
        @DisplayName("escapes backslashes in string values")
        void escapesBackslashesInValues() {
            var verdict = new ProbabilisticTestVerdictBuilder()
                    .correlationId("v:esc002")
                    .identity("test\\Class", "testMethod", "serviceContract")
                    .execution(100, 100, 95, 5, 0.9, 0.95, 1000)
                    .junitPassed(true)
                    .criterionVerdict(Verdict.PASS)
                    .build();
            String json = WebhookVerdictSink.toJson(verdict);

            assertThat(json).contains("\"testName\":\"test\\\\Class.testMethod\"");
        }

        @Test
        @DisplayName("handles empty environment metadata")
        void handlesEmptyMetadata() {
            // Builder-produced verdicts always carry the sizing-disclosure
            // entries, so the empty-map JSON shape is exercised on a record
            // stripped back to no environment entries at all.
            ProbabilisticTestVerdict built = new ProbabilisticTestVerdictBuilder()
                    .correlationId("v:empty1")
                    .identity("TestClass", "test", "uc")
                    .execution(100, 100, 95, 5, 0.9, 0.95, 1000)
                    .junitPassed(true)
                    .criterionVerdict(Verdict.PASS)
                    .build();
            ProbabilisticTestVerdict verdict = new ProbabilisticTestVerdict(
                    built.correlationId(), built.timestamp(), built.identity(),
                    built.execution(), built.functional(), built.latency(),
                    built.statistics(), built.covariates(), built.cost(),
                    built.pacing(), built.provenance(), built.termination(),
                    Map.of(), built.junitPassed(), built.punitVerdict(),
                    built.verdictReason(), built.postconditionFailures(),
                    built.perCriterion());
            String json = WebhookVerdictSink.toJson(verdict);

            assertThat(json).contains("\"environmentMetadata\":{}");
        }

        @Test
        @DisplayName("serialises the builder's sizing-disclosure entries")
        void serialisesSizingDisclosureEntries() {
            var verdict = new ProbabilisticTestVerdictBuilder()
                    .correlationId("v:sizing1")
                    .identity("TestClass", "test", "uc")
                    .execution(100, 100, 95, 5, 0.9, 0.95, 1000)
                    .junitPassed(true)
                    .criterionVerdict(Verdict.PASS)
                    .build();
            String json = WebhookVerdictSink.toJson(verdict);

            assertThat(json).contains("\"sizing-approach\":\"threshold-first\"");
        }
    }
}
