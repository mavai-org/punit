package org.mavai.punit.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.spec.TerminationReason;
import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.LatencyResult;
import org.mavai.punit.api.covariate.CovariateAlignment;
import org.mavai.punit.api.covariate.CovariateProfile;
import org.mavai.punit.api.spec.CriterionResult;
import org.mavai.punit.api.spec.CriterionRole;
import org.mavai.punit.api.spec.EngineRunSummary;
import org.mavai.punit.api.spec.EvaluatedCriterion;
import org.mavai.punit.api.spec.FailureCount;
import org.mavai.punit.api.spec.FailureExemplar;
import org.mavai.punit.api.spec.PerCriterionEvaluation;
import org.mavai.punit.api.spec.ProbabilisticTestResult;
import org.mavai.punit.api.spec.Verdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.RunMetadata;
import org.mavai.punit.internal.runtime.VerdictAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end integration test: typed result → adapter →
 * {@link VerdictXmlWriter} → XSD validation. Lives in punit-report
 * because that's where the writer and XSD resource are; punit-core
 * has the adapter unit tests but cannot reach the writer module.
 */
@DisplayName("VerdictAdapter ↔ VerdictXmlWriter round-trip")
class VerdictAdapterIntegrationTest {

    private record Factors() { }

    @Test
    @DisplayName("PASS verdict round-trips and validates against verdict-1.0.xsd")
    void passRoundTrips() throws Exception {
        ProbabilisticTestVerdict verdict = VerdictAdapter.adapt(
                richResult(Verdict.PASS),
                new RunMetadata(
                        "com.example.PaymentTest", "shouldMeetSla",
                        Optional.of("payment-gateway"),
                        Optional.empty(),
                        Map.of("region", "EU")));

        String xml = serialise(verdict);

        validateAgainstSchema(xml);

        // Spot-check key fields rendered through to XML.
        assertThat(xml).contains("use-case-id=\"payment-gateway\"");
        assertThat(xml).contains("test-name=\"shouldMeetSla\"");
        assertThat(xml).contains("planned-samples=\"100\"");
        assertThat(xml).contains("samples-executed=\"100\"");
        assertThat(xml).contains("successes=\"95\"");
        assertThat(xml).contains("failures=\"5\"");
        assertThat(xml).contains("intent=\"VERIFICATION\"");
        assertThat(xml).contains("<postcondition-failures>");
        assertThat(xml).contains("description=\"Response not empty\"");
        assertThat(xml).contains("description=\"Valid JSON\"");
        assertThat(xml).contains("origin=\"SLA\"");
        assertThat(xml).contains("spec-filename=\"payment-gateway.yaml\"");
        assertThat(xml).contains("contract-ref=\"SLA-PAY-001\"");
        assertThat(xml).contains("region");
        assertThat(xml).contains("EU");
        assertThat(xml).contains("value=\"PASS\"");
    }

    @Test
    @DisplayName("FAIL verdict round-trips and validates against verdict-1.0.xsd")
    void failRoundTrips() throws Exception {
        ProbabilisticTestVerdict verdict = VerdictAdapter.adapt(
                richResult(Verdict.FAIL),
                RunMetadata.of("com.example.PaymentTest", "shouldMeetSla", "payment-gateway"));

        String xml = serialise(verdict);
        validateAgainstSchema(xml);
        assertThat(xml).contains("value=\"FAIL\"");
    }

    @Test
    @DisplayName("INCONCLUSIVE verdict (covariate misalignment) round-trips and validates")
    void inconclusiveRoundTrips() throws Exception {
        CovariateProfile baseline = CovariateProfile.of(Map.of("model", "gpt-4"));
        CovariateProfile observed = CovariateProfile.of(Map.of("model", "gpt-4o"));
        CovariateAlignment misaligned = CovariateAlignment.compute(observed, baseline);

        ProbabilisticTestResult result = new ProbabilisticTestResult(
                Verdict.INCONCLUSIVE, FactorBundle.of(new Factors()),
                List.of(), TestIntent.VERIFICATION, List.of(),
                misaligned, Optional.empty(), Map.of(),
                EngineRunSummary.empty(), PerCriterionEvaluation.empty());

        ProbabilisticTestVerdict verdict = VerdictAdapter.adapt(
                result,
                RunMetadata.of("com.example.MyTest", "shouldRun"));

        String xml = serialise(verdict);
        validateAgainstSchema(xml);
        assertThat(xml).contains("value=\"INCONCLUSIVE\"");
        assertThat(xml).contains("<misalignment");
        assertThat(xml).contains("key=\"model\"");
        assertThat(xml).contains("baseline-value=\"gpt-4\"");
        assertThat(xml).contains("observed-value=\"gpt-4o\"");
    }

    // ── Fixture ─────────────────────────────────────────────────────

    private static ProbabilisticTestResult richResult(Verdict v) {
        LinkedHashMap<String, FailureCount> hist = new LinkedHashMap<>();
        hist.put("Response not empty", new FailureCount(2, List.of(
                new FailureExemplar("instr-1", "blank"))));
        hist.put("Valid JSON", new FailureCount(8, List.of(
                new FailureExemplar("instr-7", "missing actions"))));

        CriterionResult cr = new CriterionResult(
                "bernoulli-pass-rate", v,
                "observed=0.9500, threshold=0.9500 (origin=SLA) over 100 samples",
                Map.of("threshold", 0.95, "origin", "SLA",
                        "observed", 0.95, "successes", 95,
                        "failures", 5, "total", 100));

        return new ProbabilisticTestResult(
                v, FactorBundle.of(new Factors()),
                List.of(new EvaluatedCriterion(cr, CriterionRole.REQUIRED)),
                TestIntent.VERIFICATION, List.of(),
                CovariateAlignment.none(),
                Optional.of("SLA-PAY-001"),
                hist,
                new EngineRunSummary(
                        100, 100, 95, 5, 1500L, 12_345L, 0,
                        new LatencyResult(
                                Duration.ofMillis(120), Duration.ofMillis(340),
                                Duration.ofMillis(420), Duration.ofMillis(810),
                                100),
                        TerminationReason.COMPLETED,
                        0.95,
                        Optional.of("payment-gateway.yaml")),
                PerCriterionEvaluation.empty());
    }

    private static String serialise(ProbabilisticTestVerdict verdict) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new VerdictXmlWriter().write(verdict, baos);
        return baos.toString(StandardCharsets.UTF_8);
    }

    private void validateAgainstSchema(String xml) throws Exception {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try (InputStream xsdStream = getClass().getResourceAsStream("/org/mavai/punit/report/verdict-1.0.xsd")) {
            assertThat(xsdStream).as("XSD resource must be available").isNotNull();
            Schema schema = factory.newSchema(new StreamSource(xsdStream));
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
        }
    }
}
