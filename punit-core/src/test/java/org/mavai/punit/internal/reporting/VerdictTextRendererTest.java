package org.mavai.punit.internal.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.mavai.punit.api.TestIntent;
import org.mavai.punit.verdict.TokenMode;
import org.mavai.punit.verdict.TerminationReason;
import org.mavai.punit.api.ServiceContractAttributes;
import org.mavai.punit.statistics.transparent.TransparentStatsConfig;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.*;
import org.mavai.punit.verdict.PUnitVerdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.RunMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("VerdictTextRenderer")
class VerdictTextRendererTest {

    private VerdictTextRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new VerdictTextRenderer(true, TransparentStatsConfig.DetailLevel.VERBOSE);
    }

    @Nested
    @DisplayName("renderForReporter — verbose")
    class RenderForReporter {

        @Test
        @DisplayName("includes test name in title")
        void includesTestNameInTitle() {
            var result = renderer.renderForReporter(passingVerdict());

            assertThat(result.title()).contains("STATISTICAL ANALYSIS FOR: com.example.MyTest.shouldPass");
        }

        @Test
        @DisplayName("includes hypothesis section")
        void includesHypothesisSection() {
            var result = renderer.renderForReporter(passingVerdict());

            assertThat(result.body()).contains("HYPOTHESIS TEST");
            assertThat(result.body()).contains("H₀ (null):");
            assertThat(result.body()).contains("H₁ (alternative):");
            assertThat(result.body()).contains("Test type:");
        }

        @Test
        @DisplayName("includes observed data section")
        void includesObservedDataSection() {
            var result = renderer.renderForReporter(passingVerdict());

            assertThat(result.body()).contains("OBSERVED DATA");
            assertThat(result.body()).contains("Sample size (n):");
            assertThat(result.body()).contains("Successes (k):");
            assertThat(result.body()).contains("Observed rate (p̂):");
        }

        @Test
        @DisplayName("includes baseline reference section for spec-driven test")
        void includesBaselineReferenceSection() {
            var result = renderer.renderForReporter(verdictWithBaseline());

            assertThat(result.body()).contains("BASELINE REFERENCE");
            assertThat(result.body()).contains("Source:");
            assertThat(result.body()).contains("my-spec.yaml");
            assertThat(result.body()).contains("Empirical basis:");
        }

        @Test
        @DisplayName("includes inline threshold for non-spec test")
        void includesInlineThreshold() {
            var result = renderer.renderForReporter(passingVerdict());

            assertThat(result.body()).contains("BASELINE REFERENCE");
            assertThat(result.body()).contains("(inline threshold)");
            assertThat(result.body()).contains("explicit minPassRate");
        }

        @Test
        @DisplayName("includes statistical inference section with updated nomenclature")
        void includesStatisticalInferenceSection() {
            var result = renderer.renderForReporter(passingVerdict());

            assertThat(result.body()).contains("STATISTICAL INFERENCE");
            assertThat(result.body()).contains("SE(p̂):");
            assertThat(result.body()).contains("Wilson lower bound:");
        }

        @Test
        @DisplayName("includes Z test statistic with formula")
        void includesZTestStatistic() {
            var result = renderer.renderForReporter(passingVerdict());

            assertThat(result.body()).contains("Z:");
            assertThat(result.body()).contains("z = (p̂ - π₀) / √(π₀(1-π₀)/n)");
        }

        @Test
        @DisplayName("includes p-value with left-tailed notation")
        void includesPValueLeftTailed() {
            var result = renderer.renderForReporter(passingVerdict());

            assertThat(result.body()).contains("p-value:");
            assertThat(result.body()).contains("P(Z ≤");
        }

        @Test
        @DisplayName("includes verdict section with PASS")
        void includesVerdictSectionPass() {
            var result = renderer.renderForReporter(passingVerdict());

            assertThat(result.body()).contains("VERDICT");
            assertThat(result.body()).contains("Result:");
            assertThat(result.body()).contains("PASS");
            assertThat(result.body()).contains("Interpretation:");
        }

        @Test
        @DisplayName("includes verdict section with FAIL")
        void includesVerdictSectionFail() {
            var result = renderer.renderForReporter(failingVerdict());

            assertThat(result.body()).contains("Result:");
            assertThat(result.body()).contains("FAIL");
            assertThat(result.body()).contains("falls below");
        }

        @Test
        @DisplayName("uses box drawing characters")
        void usesBoxDrawingCharacters() {
            var result = renderer.renderForReporter(passingVerdict());

            // Body includes unicode symbols
            assertThat(result.body()).contains("π");
        }
    }

    @Nested
    @DisplayName("SUMMARY detail level")
    class SummaryDetailLevel {

        private final VerdictTextRenderer summaryRenderer =
                new VerdictTextRenderer(true, TransparentStatsConfig.DetailLevel.SUMMARY);

        @Test
        @DisplayName("omits hypothesis section")
        void omitsHypothesisSection() {
            var result = summaryRenderer.renderForReporter(passingVerdict());

            assertThat(result.body()).doesNotContain("HYPOTHESIS TEST");
        }

        @Test
        @DisplayName("omits statistical inference section")
        void omitsStatisticalInferenceSection() {
            var result = summaryRenderer.renderForReporter(passingVerdict());

            assertThat(result.body()).doesNotContain("STATISTICAL INFERENCE");
            assertThat(result.body()).doesNotContain("SE(p̂):");
            assertThat(result.body()).doesNotContain("Wilson lower bound:");
        }

        @Test
        @DisplayName("still includes observed data and verdict")
        void includesObservedDataAndVerdict() {
            var result = summaryRenderer.renderForReporter(passingVerdict());

            assertThat(result.body()).contains("OBSERVED DATA");
            assertThat(result.body()).contains("VERDICT");
            assertThat(result.body()).contains("BASELINE REFERENCE");
        }
    }

    @Nested
    @DisplayName("ASCII fallback")
    class AsciiFallback {

        @Test
        @DisplayName("uses ASCII symbols when Unicode is disabled")
        void usesAsciiSymbols() {
            VerdictTextRenderer asciiRenderer =
                    new VerdictTextRenderer(false, TransparentStatsConfig.DetailLevel.VERBOSE);

            var result = asciiRenderer.renderForReporter(passingVerdict());

            assertThat(result.title()).contains("STATISTICAL ANALYSIS FOR:");
            // ASCII fallback uses H0 instead of H₀
            assertThat(result.body()).contains("H0 (null):");
        }
    }

    @Nested
    @DisplayName("constructors")
    class Constructors {

        @Test
        @DisplayName("default constructor creates a working renderer")
        void defaultConstructor() {
            VerdictTextRenderer defaultRenderer = new VerdictTextRenderer();

            var result = defaultRenderer.renderForReporter(passingVerdict());

            assertThat(result.title()).contains("STATISTICAL ANALYSIS FOR:");
        }

        @Test
        @DisplayName("config constructor creates a working renderer")
        void configConstructor() {
            TransparentStatsConfig config = new TransparentStatsConfig(
                    true, TransparentStatsConfig.DetailLevel.SUMMARY,
                    TransparentStatsConfig.OutputFormat.CONSOLE);
            VerdictTextRenderer configRenderer = new VerdictTextRenderer(config);

            var result = configRenderer.renderForReporter(passingVerdict());

            assertThat(result.title()).contains("STATISTICAL ANALYSIS FOR:");
            assertThat(result.body()).doesNotContain("HYPOTHESIS TEST");
        }
    }

    @Nested
    @DisplayName("provenance section")
    class ProvenanceSection {

        @Test
        @DisplayName("renders provenance with threshold origin")
        void rendersProvenanceWithThresholdOrigin() {
            var result = renderer.renderForReporter(verdictWithProvenance());

            assertThat(result.body()).contains("THRESHOLD PROVENANCE");
            assertThat(result.body()).contains("Threshold origin:");
            assertThat(result.body()).contains("SLA_CONTRACT");
            assertThat(result.body()).contains("Contract:");
            assertThat(result.body()).contains("SLA-2024-001");
        }

        @Test
        @DisplayName("omits provenance section when not specified")
        void omitsProvenanceWhenNotSpecified() {
            var result = renderer.renderForReporter(passingVerdict());

            assertThat(result.body()).doesNotContain("THRESHOLD PROVENANCE");
        }
    }

    @Nested
    @DisplayName("verbose caveats")
    class VerboseCaveats {

        @Test
        @DisplayName("includes small sample size caveat")
        void includesSmallSampleSizeCaveat() {
            ProbabilisticTestVerdict verdict = verdictWithSamples(20, 18);

            var result = renderer.renderForReporter(verdict);

            assertThat(result.body()).contains("Caveat:");
            assertThat(result.body()).contains("Small sample size");
        }

        @Test
        @DisplayName("includes covariate misalignment caveat")
        void includesCovariateMisalignmentCaveat() {
            var result = renderer.renderForReporter(verdictWithMisalignment());

            assertThat(result.body()).contains("Caveat:");
            assertThat(result.body()).contains("Covariate misalignment detected");
            assertThat(result.body()).contains("model");
        }

        @Test
        @DisplayName("includes inline threshold caveat when no baseline")
        void includesInlineThresholdCaveat() {
            var result = renderer.renderForReporter(passingVerdict());

            assertThat(result.body()).contains("Using inline threshold");
        }
    }

    @Nested
    @DisplayName("latency analysis")
    class LatencyAnalysis {

        @Test
        @DisplayName("renders latency section with percentiles")
        void rendersLatencySection() {
            var result = renderer.renderForReporter(verdictWithLatency());

            assertThat(result.body()).contains("LATENCY ANALYSIS");
            assertThat(result.body()).contains("p50:");
            assertThat(result.body()).contains("120ms");
            assertThat(result.body()).contains("p95:");
            assertThat(result.body()).contains("420ms");
        }

        @Test
        @DisplayName("does not render latency section when absent")
        void doesNotRenderWhenAbsent() {
            var result = renderer.renderForReporter(passingVerdict());

            assertThat(result.body()).doesNotContain("LATENCY ANALYSIS");
        }

        @Test
        @DisplayName("renders skip message for zero successful samples")
        void rendersSkipMessage() {
            var result = renderer.renderForReporter(verdictWithSkippedLatency());

            assertThat(result.body()).contains("LATENCY ANALYSIS");
            assertThat(result.body()).contains("No successful samples available");
            assertThat(result.body()).contains("Not evaluated");
        }
    }

    @Nested
    @DisplayName("hypothesis framing")
    class HypothesisFraming {

        @Test
        @DisplayName("uses SLA framing for SLA origin")
        void usesSlaFraming() {
            ProbabilisticTestVerdict verdict = verdictWithOrigin("SLA", "SLA-001");

            var result = renderer.renderForReporter(verdict);

            assertThat(result.body()).contains("system meets SLA requirement");
        }

        @Test
        @DisplayName("uses empirical framing for EMPIRICAL origin")
        void usesEmpiricalFraming() {
            ProbabilisticTestVerdict verdict = verdictWithOrigin("EMPIRICAL", null);

            var result = renderer.renderForReporter(verdict);

            assertThat(result.body()).contains("no degradation from baseline");
        }

        @Test
        @DisplayName("uses softened framing for SMOKE intent")
        void usesSoftenedFramingForSmoke() {
            ProbabilisticTestVerdict verdict = verdictWithSmokeIntent("SLA");

            var result = renderer.renderForReporter(verdict);

            assertThat(result.body()).contains("observed rate consistent with target");
        }
    }

    @Nested
    @DisplayName("renderStatisticalAnalysisHtml")
    class RenderStatisticalAnalysisHtml {

        @Test
        @DisplayName("wraps only the label in a tooltip span, not the full line")
        void wrapsOnlyLabelInTooltipSpan() {
            String html = VerdictTextRenderer.renderStatisticalAnalysisHtml(passingVerdict());

            assertThat(html).contains(
                    "<span class=\"tip\" data-tip=\"Probability that the CI method captures the true rate\">"
                    + "Confidence level:</span>");
            // The value should be outside the span
            assertThat(html).contains("</span>   ");
        }

        @Test
        @DisplayName("wraps SE label in span with tooltip")
        void wrapsStandardErrorWithTooltip() {
            String html = VerdictTextRenderer.renderStatisticalAnalysisHtml(passingVerdict());

            assertThat(html).contains("data-tip=\"Standard error of the observed proportion");
            assertThat(html).contains("\">" + "SE(p\u0302):" + "</span>");
        }

        @Test
        @DisplayName("wraps Wilson lower bound label in span with tooltip")
        void wrapsWilsonLowerBoundWithTooltip() {
            String html = VerdictTextRenderer.renderStatisticalAnalysisHtml(passingVerdict());

            assertThat(html).contains("data-tip=\"One-sided Wilson lower bound");
            assertThat(html).contains("\">Wilson lower bound:</span>");
        }

        @Test
        @DisplayName("wraps Z label in span with tooltip")
        void wrapsZWithTooltip() {
            String html = VerdictTextRenderer.renderStatisticalAnalysisHtml(passingVerdict());

            assertThat(html).contains("data-tip=\"How many standard errors");
            assertThat(html).contains("\">Z:</span>");
        }

        @Test
        @DisplayName("wraps p-value label in span with tooltip")
        void wrapsPValueWithTooltip() {
            String html = VerdictTextRenderer.renderStatisticalAnalysisHtml(passingVerdict());

            assertThat(html).contains("data-tip=\"Probability of seeing a rate this low");
            assertThat(html).contains("\">p-value:</span>");
        }

        @Test
        @DisplayName("wraps baseline labels in spans with tooltips")
        void wrapsBaselineLabelsWithTooltips() {
            String html = VerdictTextRenderer.renderStatisticalAnalysisHtml(verdictWithBaseline());

            assertThat(html).contains("\">Baseline spec:</span>");
            assertThat(html).contains("\">Baseline samples:</span>");
            assertThat(html).contains("\">Derived threshold:</span>");
        }

        @Test
        @DisplayName("wraps covariate misalignment label in span with tooltip")
        void wrapsMisalignmentWithTooltip() {
            String html = VerdictTextRenderer.renderStatisticalAnalysisHtml(verdictWithMisalignment());

            assertThat(html).contains("data-tip=\"Test conditions that differ from the baseline");
            assertThat(html).contains("\">Covariate misalignments:</span>");
        }

        @Test
        @DisplayName("value text is not inside the tooltip span")
        void valueTextOutsideSpan() {
            String html = VerdictTextRenderer.renderStatisticalAnalysisHtml(passingVerdict());

            // "95.0%" (the value) should come after </span>, not inside it
            assertThat(html).contains("</span>").doesNotContain("<span class=\"tip\"" + "95.0%");
        }

        @Test
        @DisplayName("lines without matching label are not wrapped in span")
        void linesWithoutLabelAreNotWrapped() {
            String html = VerdictTextRenderer.renderStatisticalAnalysisHtml(passingVerdict());

            // Empty lines should not be wrapped
            assertThat(html).doesNotContain("<span class=\"tip\" data-tip=\"\">");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private ProbabilisticTestVerdict passingVerdict() {
        return minimalVerdict("shouldPass", true, PUnitVerdict.PASS, 100, 95);
    }

    private ProbabilisticTestVerdict failingVerdict() {
        return minimalVerdict("shouldFail", false, PUnitVerdict.FAIL, 100, 80);
    }

    private ProbabilisticTestVerdict verdictWithSamples(int samples, int successes) {
        return minimalVerdict("smallTest", true, PUnitVerdict.PASS, samples, successes);
    }

    private ProbabilisticTestVerdict minimalVerdict(String methodName, boolean passed,
            PUnitVerdict punitVerdict, int samples, int successes) {
        double observedRate = samples > 0 ? (double) successes / samples : 0.0;
        return new ProbabilisticTestVerdict(
                "v:test01",
                Instant.parse("2026-03-11T14:30:00Z"),
                new TestIdentity("com.example.MyTest", methodName, Optional.empty()),
                new ExecutionSummary(samples, samples, successes, samples - successes,
                        0.9, observedRate, 150,
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
                        ? String.format("%.4f >= 0.9000", (double) successes / samples)
                        : String.format("%.4f < 0.9000", (double) successes / samples)
        );
    }

    private ProbabilisticTestVerdict verdictWithBaseline() {
        ProbabilisticTestVerdict base = passingVerdict();
        StatisticalAnalysis stats = new StatisticalAnalysis(
                0.95, 0.0218, 0.8948,
                Optional.of(2.29), Optional.of(0.011),
                Optional.of("Wilson score lower bound"),
                Optional.of(new BaselineSummary(
                        "my-spec.yaml", Instant.parse("2026-02-15T00:00:00Z"),
                        1000, 940, 0.94, 0.92)),
                List.of()
        );
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), base.latency(),
                stats, base.covariates(), base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict(),
                base.verdictReason()
        );
    }

    private ProbabilisticTestVerdict verdictWithProvenance() {
        ProbabilisticTestVerdict base = passingVerdict();
        SpecProvenance prov = new SpecProvenance("SLA_CONTRACT", "SLA-2024-001",
                "payment-gateway.yaml", Optional.empty(), Optional.of("(bundled)"));
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), base.latency(),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), Optional.of(prov), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict(),
                base.verdictReason()
        );
    }

    private ProbabilisticTestVerdict verdictWithMisalignment() {
        ProbabilisticTestVerdict base = passingVerdict();
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

    private ProbabilisticTestVerdict verdictWithOrigin(String origin, String contractRef) {
        ProbabilisticTestVerdict base = passingVerdict();
        SpecProvenance prov = new SpecProvenance(origin, contractRef, "test.yaml",
                Optional.empty(), Optional.empty());
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), base.latency(),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), Optional.of(prov), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict(),
                base.verdictReason()
        );
    }

    private ProbabilisticTestVerdict verdictWithSmokeIntent(String origin) {
        ProbabilisticTestVerdict base = passingVerdict();
        ExecutionSummary exec = new ExecutionSummary(
                100, 100, 95, 5, 0.9, 0.95, 150,
                Optional.empty(), TestIntent.SMOKE, 0.95, ServiceContractAttributes.DEFAULT);
        SpecProvenance prov = new SpecProvenance(origin, null, "test.yaml",
                Optional.empty(), Optional.empty());
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), exec,
                base.functional(), base.latency(),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), Optional.of(prov), base.termination(),
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

    private ProbabilisticTestVerdict verdictWithSkippedLatency() {
        ProbabilisticTestVerdict base = passingVerdict();
        LatencyDimension latency = new LatencyDimension(
                0, 100, true, Optional.of("No successful samples"),
                -1, -1, -1, -1, -1,
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
}
