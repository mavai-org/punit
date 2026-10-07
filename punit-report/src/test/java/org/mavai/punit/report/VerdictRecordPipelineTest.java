package org.mavai.punit.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.NoFactors;
import org.mavai.punit.api.PercentileKey;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.criterion.Criteria;
import org.mavai.punit.api.criterion.LatencyCriterion;
import org.mavai.punit.api.spec.AssertionEnforcement;
import org.mavai.punit.api.spec.EnforcementMode;
import org.mavai.punit.verdict.LatencyEvaluation;
import org.mavai.punit.api.spec.ConfigurationRefusedException;
import org.mavai.punit.internal.engine.baseline.BaselineResolver;
import org.mavai.punit.runtime.PUnit;
import org.mavai.punit.statistics.ComplianceRule;
import org.mavai.punit.statistics.Methodology;
import org.mavai.punit.statistics.RegressionRule;
import org.mavai.punit.verdict.PUnitVerdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * verdict-1.8 records through the full production pipeline
 * ({@code PUnit.testing(...)} → verdict adapter → XML sink), each
 * validated against the published verdict-1.8 schema and round-tripped
 * through {@link VerdictXmlReader} and {@link VerdictXmlWriter}: a
 * typical single-criterion record, a two-criterion record, a record
 * whose latency constraint is saturated, and refused records. The
 * published worked examples are read and re-written the same way.
 */
@DisplayName("verdict-1.8 records through the production pipeline")
@TestMethodOrder(OrderAnnotation.class)
class VerdictRecordPipelineTest {

    private static final Path DIR = Path.of("build/verdict-1.8-records");

    /** The XML sink is discovered once per JVM and keeps the report
     *  directory it first resolved, so every pipeline test in this module
     *  routes its records to the same directory. */
    private static final Path XML_DIR = Path.of("build/run-design-demo/xml");

    @BeforeAll
    static void routeArtifacts() {
        System.setProperty(BaselineResolver.BASELINE_DIR_PROPERTY,
                DIR.resolve("baselines").toString());
        System.setProperty("punit.report.dir", XML_DIR.toString());
    }

    @AfterAll
    static void restoreProperties() {
        System.clearProperty(BaselineResolver.BASELINE_DIR_PROPERTY);
        System.clearProperty("punit.report.dir");
    }

    private static ServiceContract<NoFactors, Integer, Boolean> service(
            String id, Criteria<Boolean> criteria, LatencyCriterion latency) {
        return new ServiceContract<>() {
            @Override public Criteria<Boolean> criteria() { return criteria; }
            @Override public LatencyCriterion latency() {
                return latency != null ? latency : LatencyCriterion.none();
            }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
                return Outcome.ok(true);
            }
            @Override public String id() { return id; }
        };
    }

    private static Sampling<NoFactors, Integer, Boolean> sampling(
            String id, int samples, Criteria<Boolean> criteria, LatencyCriterion latency) {
        return Sampling.<NoFactors, Integer, Boolean>builder()
                .serviceContractFactory(f -> service(id, criteria, latency))
                .inputs(1, 2, 3)
                .samples(samples)
                .build();
    }

    private static Criteria<Boolean> holds() {
        return Criteria.meeting().<Boolean>zeroFailures()
                .satisfies("output is true", out ->
                        out ? Outcome.ok(out) : Outcome.fail("record", "false output"));
    }

    private static String record(String id) throws Exception {
        return Files.readString(XML_DIR.resolve(id + "." + id + ".xml"));
    }

    @Test
    @Order(1)
    @DisplayName("a typical single-criterion record")
    void typical() throws Exception {
        PUnit.testing(sampling("record-typical", 60,
                        Criteria.meeting().<Boolean>passRate(0.9).name("accuracy")
                                .satisfies("output is true", out -> Outcome.ok(out)),
                        null))
                .assertPasses();

        String xml = record("record-typical");
        assertValidRoundTrip(xml);
        assertThat(xml)
                .contains("methodology-version=\"1.6.0\"")
                .contains("decision-rule=\"compliance/exact-binomial\"")
                .contains("value=\"PASS\"");
        // required-pass is k_min, the count the compliance rule decided
        // with, at the row's own total (the run may stop early).
        Map<String, String> row = criterionRow(xml, "accuracy");
        OptionalInt kMin = ComplianceRule.minimumPassingCount(
                0.9, Integer.parseInt(row.get("total")), alphaOf(xml));
        assertThat(kMin).isPresent();
        assertThat(row).containsEntry("required-pass", Integer.toString(kMin.getAsInt()));
        assertRequiredPassConsistent(row);
    }

    @Test
    @Order(2)
    @DisplayName("a two-criterion record names each criterion's rule")
    void twoCriteria() throws Exception {
        PUnit.testing(sampling("record-two", 60,
                        Criteria.of(
                                Criteria.meeting().<Boolean>passRate(0.9).name("accuracy")
                                        .satisfies("output is true", out -> Outcome.ok(out)),
                                Criteria.meeting().<Boolean>passRate(0.5).name("lenient")
                                        .satisfies("output is true", out -> Outcome.ok(out))),
                        null))
                .assertPasses();

        String xml = record("record-two");
        assertValidRoundTrip(xml);
        assertThat(xml).contains("\"accuracy\"").contains("\"lenient\"");
        double alpha = alphaOf(xml);
        for (var c : List.of(Map.entry("accuracy", 0.9), Map.entry("lenient", 0.5))) {
            Map<String, String> row = criterionRow(xml, c.getKey());
            assertThat(row).containsEntry("required-pass", Integer.toString(
                    ComplianceRule.minimumPassingCount(c.getValue(),
                            Integer.parseInt(row.get("total")), alpha).getAsInt()));
            assertRequiredPassConsistent(row);
        }
    }

    @Test
    @Order(3)
    @DisplayName("baseline for the latency records: 20 samples")
    void measureLatencyBaseline() {
        PUnit.measuring(sampling("record-latency", 20, holds(), null))
                .experimentId("latencyBaseline")
                .run();
    }

    @Test
    @Order(4)
    @DisplayName("a saturated latency constraint is recorded as SATURATED, with no threshold")
    void saturated() throws Exception {
        // p95 over 20 test latencies against a 20-sample baseline: no
        // baseline rank keeps the breach probability within alpha.
        Throwable thrown = catchThrowable(() -> PUnit.testing(sampling("record-latency", 20,
                        holds(), Criteria.empirical().atMost(PercentileKey.P95)))
                .assertPasses());
        assertThat(thrown).isNotNull();

        String xml = record("record-latency");
        assertValidRoundTrip(xml);
        assertThat(xml)
                .contains("status=\"SATURATED\"")
                .contains("decision-rule=\"latency/precedence\"")
                .contains("value=\"INCONCLUSIVE\"");
    }

    @Test
    @Order(5)
    @DisplayName("a test larger than its baseline is refused, and the refusal is recorded")
    void refused() throws Exception {
        Throwable thrown = catchThrowable(() -> PUnit.testing(sampling("record-latency", 30,
                        holds(), Criteria.empirical().atMost(PercentileKey.P95)))
                .assertPasses());
        assertThat(thrown).isInstanceOf(ConfigurationRefusedException.class);

        String xml = record("record-latency");
        assertValidRoundTrip(xml);
        assertThat(xml)
                .contains("configuration-error=\"TEST_LARGER_THAN_BASELINE\"")
                .contains("reason=\"CONFIGURATION_REFUSED\"")
                .doesNotContain("value=\"");
        ProbabilisticTestVerdict read = new VerdictXmlReader().read(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertThat(read.decision().configurationErrors()).isNotEmpty();
    }

    /** A service whose first {@code passing} invocations return true. */
    private static Sampling<NoFactors, Integer, Boolean> scripted(
            String id, int samples, int passing, Criteria<Boolean> criteria) {
        AtomicInteger invoked = new AtomicInteger();
        return Sampling.<NoFactors, Integer, Boolean>builder()
                .serviceContractFactory(f -> new ServiceContract<NoFactors, Integer, Boolean>() {
                    @Override public Criteria<Boolean> criteria() { return criteria; }
                    @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
                        return Outcome.ok(invoked.getAndIncrement() < passing);
                    }
                    @Override public String id() { return id; }
                })
                .inputs(1, 2, 3)
                .samples(samples)
                .build();
    }

    private static Criteria<Boolean> judgedEmpirical() {
        return Criteria.empirical().<Boolean>passRate()
                .name("accuracy")
                .satisfies("output is true", out ->
                        out ? Outcome.ok(out) : Outcome.fail("record", "scripted failure"));
    }

    @Test
    @Order(6)
    @DisplayName("baseline for the regression records: 192 of 200")
    void measureRegressionBaseline() {
        PUnit.measuring(scripted("record-regression", 200, 192, judgedEmpirical()))
                .experimentId("regressionBaseline")
                .run();
    }

    @Test
    @Order(7)
    @DisplayName("a regression row states the Fisher cutoff as required-pass")
    void regressionRequiredPass() throws Exception {
        // 88 of 100 against 192 of 200: whatever the verdict, the row
        // states the cutoff the rule decided with.
        catchThrowable(() -> PUnit.testing(scripted("record-regression", 100, 88,
                        judgedEmpirical()))
                .assertPasses());

        String xml = record("record-regression");
        assertValidRoundTrip(xml);
        Map<String, String> row = criterionRow(xml, "accuracy");
        assertThat(row).containsEntry("decision-rule", "regression/fisher");
        int cutoff = RegressionRule.cutoff(
                192, 200, Integer.parseInt(row.get("total")), alphaOf(xml));
        assertThat(row).containsEntry("required-pass", Integer.toString(cutoff));
        assertRequiredPassConsistent(row);
    }

    @Test
    @Order(8)
    @DisplayName("a compliance design too small for any count to pass omits required-pass")
    void noCountCanPass() throws Exception {
        // 5 samples cannot show a 0.99 requirement at any usual alpha.
        assertThat(ComplianceRule.minimumPassingCount(0.99, 5, 0.05)).isEmpty();
        catchThrowable(() -> PUnit.testing(scripted("record-too-small", 5, 5,
                        Criteria.meeting().<Boolean>passRate(0.99).name("accuracy")
                                .satisfies("output is true", out -> Outcome.ok(out))))
                .intent(TestIntent.SMOKE)
                .assertPasses());

        String xml = record("record-too-small");
        assertValidRoundTrip(xml);
        Map<String, String> row = criterionRow(xml, "accuracy");
        assertThat(ComplianceRule.minimumPassingCount(
                0.99, Integer.parseInt(row.get("total")), alphaOf(xml))).isEmpty();
        assertThat(row)
                .containsEntry("decision-rule", "compliance/exact-binomial")
                .doesNotContainKey("required-pass");
        assertThat(row.get("verdict")).isNotEqualTo("PASS");
    }

    /** Runs {@code body} with {@code punit.advisory} set to {@code setting}. */
    private static void advisory(String setting, Runnable body) {
        System.setProperty(AssertionEnforcement.PROPERTY, setting);
        try {
            body.run();
        } finally {
            System.clearProperty(AssertionEnforcement.PROPERTY);
        }
    }

    @Test
    @Order(9)
    @DisplayName("latency advisory: the saturated constraint is reported as advisory and the test passes")
    void latencyAdvisory() throws Exception {
        advisory("latency", () -> PUnit.testing(sampling("record-latency", 20,
                        holds(), Criteria.empirical().atMost(PercentileKey.P95)))
                .assertPasses());

        String xml = record("record-latency");
        assertValidRoundTrip(xml);
        assertThat(xml)
                .contains("verdict=\"INCONCLUSIVE\" mode=\"advisory\"")
                .contains("status=\"SATURATED\"")
                .contains("<composite value=\"PASS\" mode=\"enforced\"")
                .contains("<verdict value=\"PASS\"")
                .contains("latency advisory");
    }

    @Test
    @Order(10)
    @DisplayName("latency advisory: a test larger than its baseline is still refused")
    void refusalWhateverTheSwitch() {
        Throwable thrown = catchThrowable(() -> advisory("latency", () -> PUnit.testing(
                        sampling("record-latency", 30, holds(), Criteria.empirical().atMost(PercentileKey.P95)))
                .assertPasses()));
        assertThat(thrown).isInstanceOf(ConfigurationRefusedException.class);
    }

    @Test
    @Order(11)
    @DisplayName("functional advisory: a failing requirement is decided and reported, and the test passes")
    void functionalAdvisory() throws Exception {
        advisory("functional", () -> PUnit.testing(scripted("record-functional-advisory", 60, 30,
                        Criteria.meeting().<Boolean>passRate(0.9).name("accuracy")
                                .satisfies("output is true", out ->
                                        out ? Outcome.ok(out) : Outcome.fail("record", "scripted failure"))))
                .assertPasses());

        String xml = record("record-functional-advisory");
        assertValidRoundTrip(xml);
        assertThat(criterionRow(xml, "accuracy"))
                .containsEntry("verdict", "FAIL")
                .containsEntry("decision-rule", "compliance/exact-binomial");
        assertThat(xml)
                .contains("<composite value=\"FAIL\" mode=\"advisory\"")
                .contains("<verdict value=\"PASS\"")
                .contains("functional advisory");
        // An advisory dimension's rule never decided the test.
        assertThat(Pattern.compile("<verdict [^>]*decision-rule=").matcher(xml).find()).isFalse();
    }

    @Test
    @Order(12)
    @DisplayName("unset, the same failing requirement fails the test")
    void functionalEnforcedByDefault() throws Exception {
        Throwable thrown = catchThrowable(() -> PUnit.testing(scripted("record-functional-advisory", 60, 30,
                        Criteria.meeting().<Boolean>passRate(0.9).name("accuracy")
                                .satisfies("output is true", out ->
                                        out ? Outcome.ok(out) : Outcome.fail("record", "scripted failure"))))
                .assertPasses());
        assertThat(thrown).isInstanceOf(AssertionError.class);

        String xml = record("record-functional-advisory");
        assertValidRoundTrip(xml);
        assertThat(xml)
                .contains("<composite value=\"FAIL\" mode=\"enforced\"")
                .contains("<verdict value=\"FAIL\"");
    }

    @Test
    @DisplayName("an unknown advisory setting is a configuration error before any sample runs")
    void unknownSettingRefused() {
        Throwable thrown = catchThrowable(() -> advisory("everything", () -> PUnit.testing(
                        sampling("record-unknown-setting", 10, holds(), null))
                .assertPasses()));
        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(AssertionEnforcement.PROPERTY);
    }

    /** The per-criterion row for {@code id}, as its attribute map. */
    private static Map<String, String> criterionRow(String xml, String id) {
        Matcher m = Pattern.compile("<criterion id=\"" + Pattern.quote(id) + "\"([^>]*)>")
                .matcher(xml);
        assertThat(m.find()).as("per-criterion row " + id).isTrue();
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("id", id);
        Matcher a = Pattern.compile("([\\w-]+)=\"([^\"]*)\"").matcher(m.group(1));
        while (a.find()) {
            attrs.put(a.group(1), a.group(2));
        }
        return attrs;
    }

    /** PASS iff pass >= required-pass. */
    private static void assertRequiredPassConsistent(Map<String, String> row) {
        int pass = Integer.parseInt(row.get("pass"));
        int required = Integer.parseInt(row.get("required-pass"));
        assertThat(row.get("verdict")).isEqualTo(pass >= required ? "PASS" : "FAIL");
    }

    private static double alphaOf(String xml) {
        Matcher m = Pattern.compile("<execution [^>]*confidence=\"([^\"]+)\"").matcher(xml);
        assertThat(m.find()).as("execution confidence").isTrue();
        return Methodology.alphaFromConfidence(Double.parseDouble(m.group(1)));
    }

    /** Every row's required-pass, keyed by criterion id, in document order. */
    private static Map<String, String> requiredPassByCriterion(String xml) {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = Pattern.compile("<criterion id=\"([^\"]+)\"[^>]*?required-pass=\"(\\d+)\"")
                .matcher(xml);
        while (m.find()) {
            out.put(m.group(1), m.group(2));
        }
        return out;
    }

    static Stream<String> publishedExamples() {
        return Stream.of("typical", "two-criteria", "latency-fail",
                "latency-saturated", "refused", "functional-advisory", "latency-advisory",
                "both-advisory");
    }

    @ParameterizedTest(name = "verdict-1.8-{0}.xml")
    @MethodSource("publishedExamples")
    @DisplayName("the published worked examples read and re-write as valid verdict-1.8")
    void publishedExampleRoundTrips(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(
                "/published-interchange/verdict-1.8-" + name + ".xml")) {
            assertThat(in).as("published example " + name).isNotNull();
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            validate(xml);
            assertValidRoundTrip(xml);
        }
    }

    @Test
    @DisplayName("a released verdict-1.7 record still reads, both dimensions enforced and STRICT_FAIL as FAIL")
    void verdict17RecordReads() throws Exception {
        try (InputStream in = getClass().getResourceAsStream(
                "/published-interchange/verdict-1.7-latency-fail.xml")) {
            ProbabilisticTestVerdict read = new VerdictXmlReader().read(in);
            var latency = read.latency().orElseThrow();
            assertThat(latency.mode()).contains(EnforcementMode.ENFORCED);
            assertThat(latency.evaluations())
                    .extracting(LatencyEvaluation::status)
                    .contains(LatencyEvaluation.Status.FAIL);
            assertThat(read.perCriterion().orElseThrow().mode()).isEqualTo(EnforcementMode.ENFORCED);
        }
    }

    @Test
    @DisplayName("the published worked examples state required-pass: 91, 97, 17")
    void publishedExamplesCarryRequiredPass() throws Exception {
        assertThat(requiredPassByCriterion(published("typical")))
                .containsEntry("extraction-matches-reviewed-values", "91");
        assertThat(requiredPassByCriterion(published("two-criteria")))
                .containsEntry("extraction-matches-reviewed-values-regression", "91")
                .containsEntry("extraction-matches-reviewed-values-compliance", "97");
        assertThat(requiredPassByCriterion(published("latency-saturated")))
                .containsEntry("extraction-matches-reviewed-values", "17");
    }

    private String published(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(
                "/published-interchange/verdict-1.8-" + name + ".xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void assertValidRoundTrip(String xml) throws Exception {
        validate(xml);
        ProbabilisticTestVerdict read = new VerdictXmlReader().read(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new VerdictXmlWriter().write(read, out);
        String rewritten = out.toString(StandardCharsets.UTF_8);
        validate(rewritten);
        for (String marker : List.of("status=\"SATURATED\"", "configuration-error=",
                "decision-rule=\"", "mode=\"advisory\"")) {
            if (xml.contains(marker)) {
                assertThat(rewritten).as("round trip keeps " + marker).contains(marker);
            }
        }
        assertThat(requiredPassByCriterion(rewritten))
                .as("round trip keeps every required-pass")
                .isEqualTo(requiredPassByCriterion(xml));
        assertThat(read.punitVerdict()).isIn((Object[]) PUnitVerdict.values());
    }

    private void validate(String xml) throws Exception {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try (InputStream xsd = getClass().getResourceAsStream(
                "/org/mavai/punit/report/verdict-1.8.xsd")) {
            Schema schema = factory.newSchema(new StreamSource(xsd));
            schema.newValidator().validate(new StreamSource(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
        }
    }
}
