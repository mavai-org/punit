package org.mavai.punit.report;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.NoFactors;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.criterion.Criteria;
import org.mavai.punit.internal.engine.baseline.BaselineResolver;
import org.mavai.punit.runtime.PUnit;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * The run-design disclosures through the full production pipeline:
 * {@code PUnit.testing(...)} → verdict adapter → XML sink → report
 * generator. Measures a baseline over 200 samples at a 96% criterion
 * rate, runs a downsized empirical test (80 samples, token costs
 * recorded: the full sizing trade), a declared-threshold test
 * (approach disclosure alone), and a risk-driven test (a declared
 * absolute tolerance priced to a computed count), then renders the
 * HTML report from the emitted verdict XML. Each run is its own
 * ordered test method so each verdict lands in its own XML file.
 *
 * <p>Doubles as a hands-on demo: run with
 * {@code ./gradlew :punit-report:test --tests RunDesignDisclosurePipelineTest}
 * and open {@code punit-report/build/run-design-demo/html/index.html}.
 */
@DisplayName("Run-design disclosures through the production pipeline")
@TestMethodOrder(OrderAnnotation.class)
class RunDesignDisclosurePipelineTest {

    private static final Path DEMO_DIR = Path.of("build/run-design-demo");

    @BeforeAll
    static void routeArtifactsToDemoDir() {
        System.setProperty(BaselineResolver.BASELINE_DIR_PROPERTY,
                DEMO_DIR.resolve("baselines").toString());
        System.setProperty("punit.report.dir", DEMO_DIR.resolve("xml").toString());
    }

    @AfterAll
    static void restoreProperties() {
        System.clearProperty(BaselineResolver.BASELINE_DIR_PROPERTY);
        System.clearProperty("punit.report.dir");
    }

    /** Passes exactly the first {@code passing} invocations, and records
     *  a plausible token cost per sample. */
    private static ServiceContract<NoFactors, Integer, Boolean> demoServiceContract(
            String id, int passing, Criteria<Boolean> criteria) {
        AtomicInteger invoked = new AtomicInteger();
        return new ServiceContract<>() {
            @Override public Criteria<Boolean> criteria() {
                return criteria;
            }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
                tracker.recordTokens(1200);
                // Failures are criterion-level (a judged output), not
                // apply-level, so they count into the baseline's tally.
                return Outcome.ok(invoked.getAndIncrement() < passing);
            }
            @Override public String id() { return id; }
        };
    }

    /** An empirical pass-rate criterion that judges the invocation output. */
    private static Criteria<Boolean> judgedPassRate() {
        return Criteria.empirical().<Boolean>passRate()
                .name("accuracy")
                .satisfies("output is true", out ->
                        out ? Outcome.ok(out) : Outcome.fail("demo", "scripted failure"));
    }

    private static Sampling<NoFactors, Integer, Boolean> sampling(
            String id, int samples, int passing, Criteria<Boolean> criteria) {
        return Sampling.<NoFactors, Integer, Boolean>builder()
                .serviceContractFactory(f -> demoServiceContract(id, passing, criteria))
                .inputs(1, 2, 3)
                .samples(samples)
                .build();
    }

    @Test
    @Order(1)
    @DisplayName("baseline: 200 samples at a 96% observed rate")
    void measureBaseline() {
        PUnit.measuring(sampling("demo-sized", 200, 192, judgedPassRate()))
                .experimentId("demoBaseline")
                .run();
    }

    @Test
    @Order(2)
    @DisplayName("a downsized run's verdict XML carries the full sizing trade, both cost halves")
    void downsizedRun() throws Exception {
        PUnit.testing(sampling("demo-sized", 80, Integer.MAX_VALUE, judgedPassRate()))
                .assertPasses();

        String xml = java.nio.file.Files.readString(
                DEMO_DIR.resolve("xml/demo-sized.demo-sized.xml"));
        assertThat(xml)
                .contains("key=\"sizing-approach\" value=\"sample-size-first\"")
                .contains("key=\"sizing-baseline-samples\"")
                .contains("key=\"sizing-detectable-rate\"")
                .contains("key=\"sizing-time-saved-ms\"")
                .contains("key=\"sizing-tokens-saved\"");
    }

    @Test
    @Order(3)
    @DisplayName("a declared-threshold run's verdict XML discloses the approach alone")
    void declaredThresholdRun() throws Exception {
        PUnit.testing(sampling("demo-declared", 60, Integer.MAX_VALUE, Criteria.meeting().<Boolean>passRate(0.9).name("stipulated accuracy")))
                .assertPasses();

        String xml = java.nio.file.Files.readString(
                DEMO_DIR.resolve("xml/demo-declared.demo-declared.xml"));
        assertThat(xml)
                .contains("key=\"sizing-approach\" value=\"threshold-first\"")
                .doesNotContain("sizing-detectable-rate");
    }

    @Test
    @Order(4)
    @DisplayName("a risk-driven run prices its count from the tolerance and discloses the form")
    void riskDrivenRun() throws Exception {
        // A deeper baseline: the risk-driven pricing for a 0.93 tolerance
        // demands several hundred samples, which a 200-sample baseline
        // cannot ground.
        PUnit.measuring(sampling("demo-risk", 1000, 960, judgedPassRate()))
                .experimentId("riskBaseline")
                .run();
        // The declared count is a floor (and must itself clear the
        // pre-flight feasibility gate); the tolerance prices the run
        // far above it.
        PUnit.testing(sampling("demo-risk", 100, Integer.MAX_VALUE,
                Criteria.empirical().<Boolean>passRate()
                        .name("accuracy")
                        .tolerating(0.93)
                        .satisfies("output is true", out ->
                                out ? Outcome.ok(out) : Outcome.fail("demo", "scripted failure"))))
                .assertPasses();

        String xml = java.nio.file.Files.readString(
                DEMO_DIR.resolve("xml/demo-risk.demo-risk.xml"));
        int expected = new org.mavai.punit.statistics.RiskDrivenSizingCalculator()
                .requiredSamples(0.96, 0.93, 0.95, 0.80);
        assertThat(xml)
                .contains("key=\"sizing-approach\" value=\"confidence-first (risk-driven)\"")
                .contains("key=\"sizing-tolerated-rate\" value=\"0.93\"")
                .contains("key=\"sizing-declared-power\" value=\"0.8\"")
                .contains("key=\"sizing-computed-samples\" value=\"" + expected + "\"")
                .contains("key=\"sizing-detectable-rate\"");
    }

    @Test
    @Order(5)
    @DisplayName("the HTML report renders the run-design block from the emitted XML")
    void renderReport() throws Exception {
        new ReportGenerator().generate(DEMO_DIR.resolve("xml"), DEMO_DIR.resolve("html"));
        String html = java.nio.file.Files.readString(DEMO_DIR.resolve("html/index.html"));
        assertThat(html)
                .contains("Run design")
                .contains("confidence-first (risk-driven)")
                .contains("Tolerated rate")
                .contains("would only catch a drop below")
                .contains("less execution time and tokens");
        System.out.println("report written to "
                + DEMO_DIR.resolve("html/index.html").toAbsolutePath());
    }
}
