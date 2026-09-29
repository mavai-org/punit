package org.mavai.punit.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.NoFactors;
import org.mavai.punit.api.PercentileKey;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.criterion.Criteria;
import org.mavai.punit.api.criterion.LatencyCriterion;
import org.mavai.punit.api.spec.ConfigurationRefusedException;
import org.mavai.punit.internal.engine.baseline.BaselineResolver;
import org.mavai.punit.runtime.PUnit;
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
 * verdict-1.7 records through the full production pipeline
 * ({@code PUnit.testing(...)} → verdict adapter → XML sink), each
 * validated against the published verdict-1.7 schema and round-tripped
 * through {@link VerdictXmlReader} and {@link VerdictXmlWriter}: a
 * typical single-criterion record, a two-criterion record, a record
 * whose latency constraint is saturated, and refused records. The
 * published worked examples are read and re-written the same way.
 */
@DisplayName("verdict-1.7 records through the production pipeline")
@TestMethodOrder(OrderAnnotation.class)
class VerdictRecordPipelineTest {

    private static final Path DIR = Path.of("build/verdict-1.7-records");

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
                .contains("methodology-version=\"1.5.0\"")
                .contains("decision-rule=\"compliance/exact-binomial\"")
                .contains("value=\"PASS\"");
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

    static Stream<String> publishedExamples() {
        return Stream.of("typical", "two-criteria", "latency-fail",
                "latency-saturated", "refused");
    }

    @ParameterizedTest(name = "verdict-1.7-{0}.xml")
    @MethodSource("publishedExamples")
    @DisplayName("the published worked examples read and re-write as valid verdict-1.7")
    void publishedExampleRoundTrips(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(
                "/published-interchange/verdict-1.7-" + name + ".xml")) {
            assertThat(in).as("published example " + name).isNotNull();
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            validate(xml);
            assertValidRoundTrip(xml);
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
                "decision-rule=\"")) {
            if (xml.contains(marker)) {
                assertThat(rewritten).as("round trip keeps " + marker).contains(marker);
            }
        }
        assertThat(read.punitVerdict()).isIn((Object[]) PUnitVerdict.values());
    }

    private void validate(String xml) throws Exception {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try (InputStream xsd = getClass().getResourceAsStream(
                "/org/mavai/punit/report/verdict-1.7.xsd")) {
            Schema schema = factory.newSchema(new StreamSource(xsd));
            schema.newValidator().validate(new StreamSource(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
        }
    }
}
