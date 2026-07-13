package org.mavai.punit.report;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.spec.FailureCount;
import org.mavai.punit.api.spec.FailureExemplar;
import org.mavai.punit.verdict.TokenMode;
import org.mavai.punit.internal.engine.emit.LatencySection;
import org.mavai.punit.verdict.TerminationReason;
import org.mavai.punit.api.ServiceContractAttributes;
import org.mavai.punit.verdict.CriterionRow;
import org.mavai.punit.verdict.PerCriterionStructure;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.CostSummary;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.CovariateStatus;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.ExecutionSummary;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.FunctionalDimension;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.LatencyDimension;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.Misalignment;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.SpecProvenance;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.StatisticalAnalysis;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.Termination;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.TestIdentity;
import org.mavai.punit.verdict.PUnitVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("HtmlReportWriter")
class HtmlReportWriterTest {

    @Nested
    @DisplayName("HTML structure")
    class HtmlStructure {

        @Test
        @DisplayName("produces valid HTML with DOCTYPE and head")
        void producesValidHtml() {
            String html = HtmlReportWriter.generate(List.of());

            assertThat(html).startsWith("<!DOCTYPE html>");
            assertThat(html).contains("<html lang=\"en\">");
            assertThat(html).contains("<title>PUnit Test Report</title>");
            assertThat(html).contains("<style>");
            assertThat(html).contains("</html>");
        }

        @Test
        @DisplayName("includes summary statistics")
        void includesSummaryStats() {
            String html = HtmlReportWriter.generate(List.of(
                    passingVerdict(), failingVerdict()));

            assertThat(html).contains("Total: 2");
            assertThat(html).contains("Pass: 1");
            assertThat(html).contains("Fail: 1");
        }

        @Test
        @DisplayName("includes inconclusive count when present")
        void includesInconclusiveCount() {
            String html = HtmlReportWriter.generate(List.of(inconclusiveVerdict()));

            assertThat(html).contains("Inconclusive: 1");
        }

        @Test
        @DisplayName("omits inconclusive count when zero")
        void omitsInconclusiveWhenZero() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).doesNotContain("Inconclusive");
        }
    }

    @Nested
    @DisplayName("table rows")
    class TableRows {

        @Test
        @DisplayName("the verdict table carries a single Verdict column — the JUnit "
                + "column was retired in 0.7.0")
        void tableHeaderShape() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("<th>Verdict</th>");
            assertThat(html)
                    .doesNotContain("<th>JUnit</th>")
                    .doesNotContain("<th>PUnit</th>");
        }

        @Test
        @DisplayName("renders test method name as expandable summary")
        void rendersMethodName() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("<summary>shouldPass</summary>");
        }

        @Test
        @DisplayName("applies the punit-pass CSS class on the verdict cell for a passing verdict")
        void appliesPassCssClasses() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("class=\"punit-pass\"");
            assertThat(html)
                    .as("the JUnit column was retired in 0.7.0 — the verdict cell "
                            + "carries the punit verdict only")
                    .doesNotContain("junit-pass")
                    .doesNotContain("junit-fail");
        }

        @Test
        @DisplayName("applies the punit-fail CSS class on the verdict cell for a failing verdict")
        void appliesFailCssClasses() {
            String html = HtmlReportWriter.generate(List.of(failingVerdict()));

            assertThat(html).contains("class=\"punit-fail\"");
            assertThat(html)
                    .doesNotContain("junit-pass")
                    .doesNotContain("junit-fail");
        }

        @Test
        @DisplayName("applies correct CSS class for inconclusive verdict")
        void appliesInconclusiveCssClass() {
            String html = HtmlReportWriter.generate(List.of(inconclusiveVerdict()));

            assertThat(html).contains("class=\"punit-inconclusive\"");
        }

        @Test
        @DisplayName("shows functional dimension when present")
        void showsFunctionalDimension() {
            String html = HtmlReportWriter.generate(List.of(verdictWithFunctional()));

            assertThat(html).contains("95/100");
        }

        @Test
        @DisplayName("shows dash for absent functional dimension")
        void showsDashForAbsentFunctional() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("<td>-</td>");
        }

        @Test
        @DisplayName("shows latency p50, p95, and p99 when present")
        void showsLatencyPercentiles() {
            String html = HtmlReportWriter.generate(List.of(verdictWithLatency()));

            assertThat(html).contains("<th>p50</th>");
            assertThat(html).contains("<th>p95</th>");
            assertThat(html).contains("<th>p99</th>");
            assertThat(html).contains("120ms");   // p50
            assertThat(html).contains("420ms");   // p95
            assertThat(html).contains("810ms");   // p99
        }

        @Test
        @DisplayName("shows samples as executed/planned")
        void showsSamples() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("100/100");
        }

        @Test
        @DisplayName("shows elapsed time")
        void showsElapsed() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("150ms");
        }
    }

    @Nested
    @DisplayName("expand/collapse structure")
    class ExpandCollapse {

        @Test
        @DisplayName("includes level 2 summary text in pre block")
        void includesLevel2Summary() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("class=\"level2\"");
            assertThat(html).contains("Observed pass rate:");
        }

        @Test
        @DisplayName("includes nested details for statistical analysis")
        void includesNestedStatisticalAnalysis() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("<summary>Statistical Analysis</summary>");
            assertThat(html).contains("class=\"level3\"");
            assertThat(html).contains("Confidence level:");
        }
    }

    @Nested
    @DisplayName("grouping")
    class Grouping {

        @Test
        @DisplayName("groups by use-case-id when present")
        void groupsByServiceContractId() {
            ProbabilisticTestVerdict verdict = verdictWithServiceContractId("payment-gateway");

            String html = HtmlReportWriter.generate(List.of(verdict));

            assertThat(html).contains("<h2>payment-gateway</h2>");
        }

        @Test
        @DisplayName("groups by class name when use-case-id absent")
        void groupsByClassName() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("<h2>com.example.MyTest</h2>");
        }
    }

    @Nested
    @DisplayName("detail panel indentation")
    class DetailPanelIndentation {

        @Test
        @DisplayName("level2 and level3 blocks have left margin in CSS")
        void detailPanelsAreIndented() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("margin: 0.5rem 0 0.5rem 1.5rem");
            assertThat(html).contains("details details");
        }
    }

    @Nested
    @DisplayName("inconclusive guidance")
    class InconclusiveGuidance {

        @Test
        @DisplayName("shows report-level banner when inconclusive verdicts exist")
        void showsReportLevelBanner() {
            String html = HtmlReportWriter.generate(List.of(inconclusiveVerdictWithMisalignment()));

            assertThat(html).contains("banner-inconclusive");
            assertThat(html).contains("1 test has an inconclusive verdict");
            assertThat(html).contains("Re-run experiments to produce baselines");
            assertThat(html).contains("./gradlew exp");
        }

        @Test
        @DisplayName("banner uses plural form for multiple inconclusive verdicts")
        void showsPluralBanner() {
            String html = HtmlReportWriter.generate(List.of(
                    inconclusiveVerdictWithMisalignment(),
                    inconclusiveVerdictWithMisalignment()));

            assertThat(html).contains("2 tests have an inconclusive verdict");
        }

        @Test
        @DisplayName("no banner when no inconclusive verdicts")
        void noBannerWhenNoneInconclusive() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).doesNotContain("class=\"banner-inconclusive\"");
        }

        @Test
        @DisplayName("shows test-level misalignment guidance with experiment command")
        void showsTestLevelGuidance() {
            String html = HtmlReportWriter.generate(List.of(inconclusiveVerdictWithMisalignment()));

            assertThat(html).contains("misalignment-guidance");
            assertThat(html).contains("Covariate misalignment");
            assertThat(html).contains("model: baseline=");
            assertThat(html).contains("./gradlew exp -Prun=MyTest");
        }

        @Test
        @DisplayName("no test-level guidance for passing verdicts")
        void noGuidanceForPassingVerdicts() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).doesNotContain("class=\"misalignment-guidance\"");
        }
    }

    @Nested
    @DisplayName("baseline provenance in detail")
    class BaselineProvenance {

        @Test
        @DisplayName("level 2 block contains baseline filename when provenance present")
        void level2ContainsBaselineFilename() {
            String html = HtmlReportWriter.generate(List.of(verdictWithProvenance()));

            assertThat(html).contains("Baseline:");
            assertThat(html).contains("payment-gateway.yaml");
        }
    }

    @Nested
    @DisplayName("statistical tooltips")
    class StatisticalTooltips {

        @Test
        @DisplayName("level 3 block contains tooltip spans on labels only")
        void level3ContainsTooltipSpans() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("<span class=\"tip\" data-tip=");
            assertThat(html).contains("\">Confidence level:</span>");
        }

        @Test
        @DisplayName("CSS includes tooltip styles for hover pseudo-element")
        void cssIncludesTooltipStyles() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("pre.level3 span.tip");
            assertThat(html).contains("cursor: help");
            assertThat(html).contains("span.tip:hover::after");
            assertThat(html).contains("content: attr(data-tip)");
        }
    }

    @Nested
    @DisplayName("statistical assumptions")
    class StatisticalAssumptions {

        @Test
        @DisplayName("includes collapsed assumptions section in every report")
        void includesAssumptionsSection() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("<details class=\"assumptions\">");
            assertThat(html).contains("<summary>Statistical assumptions and limitations</summary>");
        }

        @Test
        @DisplayName("assumptions section is present even for empty reports")
        void presentForEmptyReports() {
            String html = HtmlReportWriter.generate(List.of());

            assertThat(html).contains("<details class=\"assumptions\">");
        }

        @Test
        @DisplayName("assumptions section appears between header and main content")
        void appearsBeforeMainContent() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            int headerEnd = html.indexOf("</header>");
            int assumptionsStart = html.indexOf("<details class=\"assumptions\">");
            int mainStart = html.indexOf("<main>");

            assertThat(assumptionsStart).isGreaterThan(headerEnd);
            assertThat(assumptionsStart).isLessThan(mainStart);
        }

        @Test
        @DisplayName("contains key assumption bullet points")
        void containsKeyAssumptions() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html)
                    .contains("Binary outcome")
                    .contains("Same question each time")
                    .contains("Unchanged threshold")
                    .contains("Independence")
                    .contains("No major drift during sampling");
        }

        @Test
        @DisplayName("contains warning about assumption-violating test patterns")
        void containsWarning() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("assumptions-warning");
            assertThat(html).contains("warm up, exhaust, mutate, learn, cache, throttle, or degrade");
        }

        @Test
        @DisplayName("includes CSS for assumptions styling")
        void includesCss() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains(".assumptions");
            assertThat(html).contains(".assumptions-body");
            assertThat(html).contains(".assumptions-warning");
        }
    }

    @Nested
    @DisplayName("HTML escaping")
    class HtmlEscaping {

        @Test
        @DisplayName("escapes special characters in test names")
        void escapesSpecialCharacters() {
            ProbabilisticTestVerdict verdict = verdictWithMethodName("test<script>alert('xss')</script>");

            String html = HtmlReportWriter.generate(List.of(verdict));

            assertThat(html).doesNotContain("<script>");
            assertThat(html).contains("&lt;script&gt;");
        }
    }

    @Nested
    @DisplayName("latency colour coding")
    class LatencyColourCoding {

        @Test
        @DisplayName("observational latency renders values without colour class")
        void observationalLatencyRendersWithoutColour() {
            String html = HtmlReportWriter.generate(List.of(verdictWithLatency()));

            assertThat(html).contains("class=\"latency-observed\">120ms</td>");
            assertThat(html).contains("class=\"latency-observed\">420ms</td>");
            assertThat(html).contains("class=\"latency-observed\">810ms</td>");
            assertThat(html).doesNotContain("class=\"latency-pass\">");
            assertThat(html).doesNotContain("class=\"latency-fail\">");
        }

        @Test
        @DisplayName("unavailable percentile (below-minimum sentinel) renders as a dash")
        void unavailablePercentileRendersAsDash() {
            // p99 below the minimum (100 contributing samples) is
            // emitted as PERCENTILE_UNAVAILABLE_MS by the adapter; the
            // HTML cell must show "-" rather than "-1ms" or a literal
            // sentinel value.
            ProbabilisticTestVerdict base = passingVerdict();
            LatencyDimension latency = new LatencyDimension(
                    90, 100, false, Optional.empty(),
                    120, 340, 420,
                    LatencySection.PERCENTILE_UNAVAILABLE_MS,
                    LatencySection.PERCENTILE_UNAVAILABLE_MS,
                    List.of()
            );
            ProbabilisticTestVerdict verdict = new ProbabilisticTestVerdict(
                    base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                    base.functional(), Optional.of(latency),
                    base.statistics(), base.covariates(), base.cost(),
                    base.pacing(), base.provenance(), base.termination(),
                    base.environmentMetadata(), base.junitPassed(), base.punitVerdict(),
                    base.verdictReason()
            );

            String html = HtmlReportWriter.generate(List.of(verdict));

            assertThat(html).contains("<td class=\"latency-observed\">120ms</td>"); // p50 present
            assertThat(html).contains("<td class=\"latency-observed\">420ms</td>"); // p95 present
            assertThat(html).contains("<td class=\"latency-observed\">-</td>");     // p99 unavailable
            assertThat(html).doesNotContain("<td class=\"latency-observed\">-1ms");
            assertThat(html).doesNotContain("<td class=\"latency-observed\">-ms");
        }
    }

    @Nested
    @DisplayName("postcondition failures section")
    class PostconditionFailuresSection {

        @Test
        @DisplayName("renders nested details with table when histogram is non-empty")
        void rendersTableWhenPopulated() {
            String html = HtmlReportWriter.generate(List.of(verdictWithPostconditionFailures()));

            assertThat(html).contains("<summary>Postcondition Failures</summary>");
            assertThat(html).contains("<table class=\"postcondition-failures\">");
            assertThat(html).contains("<th>Clause</th>");
            assertThat(html).contains("<th>Count</th>");
            assertThat(html).contains("<th>Exemplars</th>");
        }

        @Test
        @DisplayName("clauses appear in declaration (insertion) order")
        void clausesInDeclarationOrder() {
            String html = HtmlReportWriter.generate(List.of(verdictWithPostconditionFailures()));

            int firstIdx = html.indexOf("Response not empty");
            int secondIdx = html.indexOf("Valid JSON");
            assertThat(firstIdx).isPositive();
            assertThat(secondIdx).isGreaterThan(firstIdx);
        }

        @Test
        @DisplayName("exemplars rendered as bullet list with code-formatted input")
        void exemplarsRenderedAsList() {
            String html = HtmlReportWriter.generate(List.of(verdictWithPostconditionFailures()));

            assertThat(html).contains("<code>instr-7</code>");
            assertThat(html).contains("missing actions");
        }

        @Test
        @DisplayName("section omitted entirely when histogram is empty")
        void omittedWhenEmpty() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).doesNotContain("Postcondition Failures");
            assertThat(html).doesNotContain("table class=\"postcondition-failures\"");
        }

        @Test
        @DisplayName("CSS includes postcondition-failures rules")
        void cssRulesPresent() {
            String html = HtmlReportWriter.generate(List.of(verdictWithPostconditionFailures()));

            assertThat(html).contains("table.postcondition-failures");
            assertThat(html).contains(".postcondition-failures td.count");
        }

        @Test
        @DisplayName("escapes XML special characters in clause and exemplar fields")
        void escapesSpecialCharacters() {
            LinkedHashMap<String, FailureCount> hist = new LinkedHashMap<>();
            hist.put("clause with <chevrons> & \"quotes\"", new FailureCount(1, List.of(
                    new FailureExemplar("input <bad>", "reason has \" & < & >"))));
            ProbabilisticTestVerdict verdict = postconditionFailuresVerdict(hist);

            String html = HtmlReportWriter.generate(List.of(verdict));

            assertThat(html).contains("clause with &lt;chevrons&gt; &amp; &quot;quotes&quot;");
            assertThat(html).contains("input &lt;bad&gt;");
            assertThat(html).contains("reason has &quot; &amp; &lt; &amp; &gt;");
        }
    }

    @Nested
    @DisplayName("Per-criterion breakdown")
    class PerCriterionBreakdown {

        @Test
        @DisplayName("renders a Per-criterion breakdown <details> block when perCriterion is present")
        void rendersBlockWhenPresent() {
            PerCriterionStructure pc = new PerCriterionStructure(
                    List.of(new CriterionRow(
                            "bernoulli-pass-rate",
                            org.mavai.punit.api.spec.Verdict.PASS,
                            95, 5, 0, 0.95, 0.9)),
                    org.mavai.punit.api.spec.Verdict.PASS);

            String html = HtmlReportWriter.generate(List.of(verdictWithPerCriterion(pc)));

            assertThat(html).contains("<summary>Per-criterion breakdown</summary>");
            assertThat(html).contains("bernoulli-pass-rate");
            assertThat(html).contains("0.9500");
            assertThat(html).contains("0.9000");
        }

        @Test
        @DisplayName("omits the block entirely when perCriterion is empty")
        void omitsBlockWhenAbsent() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).doesNotContain("Per-criterion breakdown");
        }

        @Test
        @DisplayName("renders NaN observed-rate and threshold as a dash, not the literal NaN")
        void rendersNaNAsDash() {
            PerCriterionStructure pc = new PerCriterionStructure(
                    List.of(new CriterionRow(
                            "bernoulli-pass-rate",
                            org.mavai.punit.api.spec.Verdict.INCONCLUSIVE,
                            0, 0, 0, Double.NaN, Double.NaN)),
                    org.mavai.punit.api.spec.Verdict.INCONCLUSIVE);

            String html = HtmlReportWriter.generate(List.of(verdictWithPerCriterion(pc)));

            assertThat(html).contains("<summary>Per-criterion breakdown</summary>");
            assertThat(html).contains("&mdash;");
            assertThat(html).doesNotContain(">NaN<");
        }

        @Test
        @DisplayName("each row's verdict carries the matching three-valued CSS class")
        void verdictBadgeCarriesCssClass() {
            PerCriterionStructure failingPc = new PerCriterionStructure(
                    List.of(new CriterionRow(
                            "bernoulli-pass-rate",
                            org.mavai.punit.api.spec.Verdict.FAIL,
                            65, 35, 0, 0.65, 0.9)),
                    org.mavai.punit.api.spec.Verdict.FAIL);

            String html = HtmlReportWriter.generate(List.of(verdictWithPerCriterion(failingPc)));

            // The verdict badge inside the per-criterion block is wrapped in
            // a span carrying the verdict's CSS class.
            assertThat(html).contains("<span class=\"punit-fail\">FAIL</span>");
        }
    }

    @Nested
    @DisplayName("Run design block")
    class RunDesignBlock {

        @Test
        @DisplayName("discloses the approach and the paired sizing trade")
        void disclosesApproachAndSizingTrade() {
            Map<String, String> env = new LinkedHashMap<>();
            env.put("sizing-approach", "sample-size-first");
            env.put("sizing-declared-samples", "100");
            env.put("sizing-declared-confidence", "0.95");
            env.put("sizing-baseline-samples", "1000");
            env.put("sizing-detectable-rate", "0.876");
            env.put("sizing-detectable-power", "0.8");
            env.put("sizing-saved-fraction", "0.9");
            env.put("sizing-time-saved-ms", "45000");
            env.put("sizing-tokens-saved", "1080000");

            String html = HtmlReportWriter.generate(List.of(downsizedVerdict(env)));

            assertThat(html).contains("<summary>Run design</summary>");
            assertThat(html).contains("sample-size-first &mdash; the sample size was chosen first");
            assertThat(html).contains("<dt>Declared samples</dt><dd>100</dd>");
            assertThat(html).contains("<dt>Confidence</dt><dd>95%</dd>");
            assertThat(html).contains(
                    "This test was sized at 100 samples against a baseline measured over 1000.");
            assertThat(html).contains(
                    "would only catch a drop below 88% four times out of five.");
            assertThat(html).contains("about 90% less execution time and tokens");
            assertThat(html).contains("roughly 45.0 seconds and 1080000 tokens");
            assertThat(html).contains("Estimates only.");
        }

        @Test
        @DisplayName("degrades the efficiency disclosure to time-only without token costs")
        void degradesToTimeOnlyWithoutTokenCosts() {
            Map<String, String> env = new LinkedHashMap<>();
            env.put("sizing-approach", "sample-size-first");
            env.put("sizing-declared-samples", "100");
            env.put("sizing-declared-confidence", "0.95");
            env.put("sizing-baseline-samples", "1000");
            env.put("sizing-detectable-rate", "0.876");
            env.put("sizing-detectable-power", "0.8");
            env.put("sizing-saved-fraction", "0.9");
            env.put("sizing-time-saved-ms", "45000");

            String html = HtmlReportWriter.generate(List.of(downsizedVerdict(env)));

            assertThat(html).contains("about 90% less execution time (roughly 45.0 seconds");
            assertThat(html).contains("no token figures are recorded for this run.");
            assertThat(html).doesNotContain("less execution time and tokens");
        }

        @Test
        @DisplayName("the approach stands alone on a full-size run")
        void approachStandsAloneOnFullSizeRun() {
            Map<String, String> env = new LinkedHashMap<>();
            env.put("sizing-approach", "threshold-first");
            env.put("sizing-declared-samples", "100");
            env.put("sizing-declared-min-pass-rate", "0.9");

            String html = HtmlReportWriter.generate(List.of(downsizedVerdict(env)));

            assertThat(html).contains("<summary>Run design</summary>");
            assertThat(html).contains("threshold-first &mdash; the pass bar is externally stipulated");
            assertThat(html).contains("<dt>Minimum pass rate</dt><dd>90%</dd>");
            assertThat(html).doesNotContain("would only catch a drop below");
            assertThat(html).doesNotContain("Estimated saving");
        }

        @Test
        @DisplayName("omits the block for verdicts carrying no sizing facts")
        void omitsBlockWithoutSizingFacts() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).doesNotContain("Run design");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * A verdict carrying the given environment entries and a resolved
     * baseline measured over 1,000 samples — the shape a downsized
     * spec-driven run records.
     */
    private ProbabilisticTestVerdict downsizedVerdict(Map<String, String> env) {
        ProbabilisticTestVerdict base = passingVerdict();
        StatisticalAnalysis stats = new StatisticalAnalysis(
                0.95, 0.0218, 0.8948,
                Optional.of(2.29), Optional.of(0.011),
                Optional.of("Wilson"),
                Optional.of(new ProbabilisticTestVerdict.BaselineSummary(
                        "svc.yaml", Instant.parse("2026-07-01T00:00:00Z"),
                        1000, 960, 0.96, 0.9)),
                List.of());
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), base.latency(), stats, base.covariates(),
                base.cost(), base.pacing(), base.provenance(), base.termination(),
                env, base.junitPassed(), base.punitVerdict(),
                base.verdictReason(), base.postconditionFailures(), base.perCriterion());
    }

    private ProbabilisticTestVerdict verdictWithPerCriterion(PerCriterionStructure pc) {
        ProbabilisticTestVerdict base = passingVerdict();
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), base.latency(), base.statistics(), base.covariates(),
                base.cost(), base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict(),
                base.verdictReason(), base.postconditionFailures(), Optional.of(pc));
    }

    private ProbabilisticTestVerdict passingVerdict() {
        return minimalVerdict("shouldPass", true, PUnitVerdict.PASS);
    }

    private ProbabilisticTestVerdict failingVerdict() {
        return minimalVerdict("shouldFail", false, PUnitVerdict.FAIL);
    }

    private ProbabilisticTestVerdict inconclusiveVerdict() {
        return minimalVerdict("shouldBeInconclusive", false, PUnitVerdict.INCONCLUSIVE);
    }

    private ProbabilisticTestVerdict minimalVerdict(String methodName, boolean passed, PUnitVerdict punitVerdict) {
        return new ProbabilisticTestVerdict(
                "v:test01",
                Instant.parse("2026-03-11T14:30:00Z"),
                new TestIdentity("com.example.MyTest", methodName, Optional.empty()),
                new ExecutionSummary(100, 100, 95, 5, 0.9, 0.95, 150,
                        Optional.empty(), TestIntent.VERIFICATION, 0.95, ServiceContractAttributes.DEFAULT),
                Optional.empty(), Optional.empty(),
                new StatisticalAnalysis(0.95, 0.0218, 0.8948,
                        Optional.of(2.29), Optional.of(0.011),
                        Optional.empty(), Optional.empty(), List.of()),
                CovariateStatus.allAligned(),
                new CostSummary(0, 0, 0, TokenMode.NONE, Optional.empty(), Optional.empty()),
                Optional.empty(), Optional.empty(),
                new Termination(TerminationReason.COMPLETED, Optional.empty()),
                Map.of(), passed, punitVerdict,
                punitVerdict == PUnitVerdict.PASS
                        ? "0.9500 >= 0.9000"
                        : punitVerdict == PUnitVerdict.INCONCLUSIVE
                                ? "covariate misalignment"
                                : "0.9500 < 0.9000"
        );
    }

    private ProbabilisticTestVerdict verdictWithFunctional() {
        ProbabilisticTestVerdict base = passingVerdict();
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                Optional.of(new FunctionalDimension(95, 5, 0.95)),
                base.latency(), base.statistics(), base.covariates(), base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict(),
                base.verdictReason()
        );
    }

    private ProbabilisticTestVerdict verdictWithLatency() {
        ProbabilisticTestVerdict base = passingVerdict();
        LatencyDimension latency = new LatencyDimension(
                90, 100, false, Optional.empty(),
                120, 340, 420, 810, 1250,
                List.of()
        );
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), Optional.of(latency),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict(),
                base.verdictReason()
        );
    }

    private ProbabilisticTestVerdict verdictWithServiceContractId(String serviceContractId) {
        ProbabilisticTestVerdict base = passingVerdict();
        TestIdentity identity = new TestIdentity(
                base.identity().className(), base.identity().methodName(), Optional.of(serviceContractId));
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), identity, base.execution(),
                base.functional(), base.latency(),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict(),
                base.verdictReason()
        );
    }

    private ProbabilisticTestVerdict verdictWithMethodName(String methodName) {
        return minimalVerdict(methodName, true, PUnitVerdict.PASS);
    }

    private ProbabilisticTestVerdict verdictWithProvenance() {
        ProbabilisticTestVerdict base = passingVerdict();
        SpecProvenance prov = new SpecProvenance("SLA", "SLA-PAY-001", "payment-gateway.yaml",
                Optional.empty(), Optional.of("(bundled)"));
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), base.latency(),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), Optional.of(prov), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict(),
                base.verdictReason()
        );
    }

    private ProbabilisticTestVerdict verdictWithPostconditionFailures() {
        LinkedHashMap<String, FailureCount> hist = new LinkedHashMap<>();
        hist.put("Response not empty", new FailureCount(2, List.of(
                new FailureExemplar("instr-1", "blank"),
                new FailureExemplar("instr-2", "blank"))));
        hist.put("Valid JSON", new FailureCount(8, List.of(
                new FailureExemplar("instr-7", "missing actions"),
                new FailureExemplar("instr-9", "unexpected token"),
                new FailureExemplar("instr-12", "trailing comma"))));
        return postconditionFailuresVerdict(hist);
    }

    private ProbabilisticTestVerdict postconditionFailuresVerdict(
            Map<String, FailureCount> hist) {
        ProbabilisticTestVerdict base = passingVerdict();
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), base.latency(), base.statistics(), base.covariates(),
                base.cost(), base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict(),
                base.verdictReason(),
                hist
        );
    }

    private ProbabilisticTestVerdict inconclusiveVerdictWithMisalignment() {
        ProbabilisticTestVerdict base = minimalVerdict("shouldBeInconclusive", false, PUnitVerdict.INCONCLUSIVE);
        CovariateStatus cov = new CovariateStatus(false,
                List.of(new Misalignment("model", "gpt-4", "gpt-4o")),
                Map.of(), Map.of());
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), base.latency(),
                base.statistics(), cov, base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict(),
                "covariate misalignment"
        );
    }
}
