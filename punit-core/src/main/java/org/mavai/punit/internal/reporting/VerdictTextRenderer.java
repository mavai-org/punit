package org.mavai.punit.internal.reporting;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mavai.punit.api.TestIntent;
import org.mavai.punit.internal.engine.emit.LatencySection;
import org.mavai.punit.verdict.TerminationReason;
import org.mavai.punit.internal.reporting.PUnitReporter;
import org.mavai.punit.internal.reporting.RateFormat;
import org.mavai.punit.statistics.ComplianceEvidenceEvaluator;
import org.mavai.punit.statistics.VerificationFeasibilityEvaluator;
import org.mavai.punit.statistics.transparent.StatisticalVocabulary;
import org.mavai.punit.statistics.transparent.TransparentStatsConfig;
import org.mavai.punit.verdict.PUnitVerdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.BaselineSummary;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.ExecutionSummary;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.FunctionalDimension;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.LatencyDimension;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.Misalignment;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.SpecProvenance;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.StatisticalAnalysis;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.Termination;

/**
 * Renders verdict text at all detail levels from a {@link ProbabilisticTestVerdict}.
 *
 * <p>This is the single rendering path for verdict text. It replaces the former
 * dual-renderer design (punit-report VerdictTextRenderer + punit-core TextExplanationRenderer)
 * with a consolidated implementation that consumes the verdict model directly.
 *
 * <p>Provides three rendering levels:
 * <ul>
 *   <li>{@link #renderSummary} — compact pass/fail line for HTML report Level 2</li>
 *   <li>{@link #renderStatisticalAnalysis} — statistical details for HTML report Level 3</li>
 *   <li>{@link #renderForReporter} — verbose box-drawn output for transparent stats console</li>
 * </ul>
 */
// mavai-ref: JVI-ZVX9J8V — do not remove (resolves in mavai-orchestrator)
public final class VerdictTextRenderer {

    private static final int LINE_WIDTH = 78;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private final StatisticalVocabulary.Symbols symbols;
    private final TransparentStatsConfig.DetailLevel detailLevel;

    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════════════════

    public VerdictTextRenderer() {
        this(TransparentStatsConfig.supportsUnicode(), TransparentStatsConfig.DetailLevel.VERBOSE);
    }

    public VerdictTextRenderer(boolean useUnicode, TransparentStatsConfig.DetailLevel detailLevel) {
        this.symbols = StatisticalVocabulary.symbols(useUnicode);
        this.detailLevel = detailLevel;
    }

    public VerdictTextRenderer(TransparentStatsConfig config) {
        this(TransparentStatsConfig.supportsUnicode(), config.detailLevel());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATIC METHODS — HTML report (compact)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Renders a summary-level text description of the verdict (Level 2).
     */
    public static String renderSummary(ProbabilisticTestVerdict verdict) {
        ExecutionSummary exec = verdict.execution();
        Termination term = verdict.termination();

        StringBuilder sb = new StringBuilder();

        if (exec.warmup() > 0) {
            String serviceContractLabel = verdict.identity().serviceContractId().orElse("");
            sb.append(PUnitReporter.labelValueLn("Warmup:",
                    String.format("%d invocations discarded%s",
                            exec.warmup(),
                            serviceContractLabel.isEmpty() ? "" : " (" + serviceContractLabel + ")")));
        }

        if (term.reason().isBudgetExhaustion()) {
            sb.append(PUnitReporter.labelValueLn("Samples executed:",
                    String.format("%d of %d (budget exhausted)",
                            exec.samplesExecuted(), exec.plannedSamples())));
            sb.append(PUnitReporter.labelValueLn("Pass rate:",
                    String.format("%s (%d/%d), required: %s",
                            RateFormat.format(exec.observedPassRate()),
                            exec.successes(), exec.samplesExecuted(),
                            RateFormat.format(exec.minPassRate()))));
        } else {
            String comparator = exec.observedPassRate() >= exec.minPassRate() ? ">=" : "<";
            sb.append(PUnitReporter.labelValueLn("Observed pass rate:",
                    String.format("%s (%d/%d) %s required: %s",
                            RateFormat.format(exec.observedPassRate()),
                            exec.successes(), exec.samplesExecuted(),
                            comparator,
                            RateFormat.format(exec.minPassRate()))));
        }

        appendDimensionBreakdown(sb, verdict);

        if (verdict.punitVerdict() == PUnitVerdict.INCONCLUSIVE) {
            sb.append(PUnitReporter.labelValueLn("Verdict:",
                    "Inconclusive \u2014 " + verdict.verdictReason()));
        }

        if (term.reason() != TerminationReason.COMPLETED) {
            sb.append(PUnitReporter.labelValueLn("Termination:",
                    formatTerminationMessage(term.reason(), exec.samplesExecuted(), exec.plannedSamples())));
        }

        sb.append(PUnitReporter.labelValueLn("Elapsed:", exec.elapsedMs() + "ms"));

        appendBaselineLine(sb, verdict);

        return sb.toString();
    }

    /**
     * Renders the statistical analysis (Level 3).
     */
    public static String renderStatisticalAnalysis(ProbabilisticTestVerdict verdict) {
        StatisticalAnalysis stats = verdict.statistics();
        StringBuilder sb = new StringBuilder();

        stats.baseline().ifPresent(b -> {
            sb.append(PUnitReporter.labelValueLn("Baseline spec:", b.sourceFile()));
            sb.append(PUnitReporter.labelValueLn("Baseline samples:",
                    String.format("%d (%d successes, rate: %s)",
                            b.baselineSamples(), b.baselineSuccesses(),
                            RateFormat.format(b.baselineRate()))));
            String derivationTag = stats.thresholdDerivation()
                    .map(d -> " (" + d + ")").orElse("");
            sb.append(PUnitReporter.labelValueLn("Derived threshold:",
                    RateFormat.format(b.derivedThreshold()) + derivationTag));
            sb.append("\n");
        });

        sb.append(PUnitReporter.labelValueLn("Confidence level:",
                String.format("%.1f%%", stats.confidenceLevel() * 100)));
        sb.append(PUnitReporter.labelValueLn("SE(p\u0302):",
                String.format("%.4f", stats.standardError())));
        sb.append(PUnitReporter.labelValueLn("Wilson lower bound:",
                String.format("%.4f", stats.wilsonLower())));

        stats.testStatistic().ifPresent(t ->
                sb.append(PUnitReporter.labelValueLn("Z:", String.format("%.4f", t))));
        stats.pValue().ifPresent(p ->
                sb.append(PUnitReporter.labelValueLn("p-value:", String.format("%.4f", p))));

        if (!stats.caveats().isEmpty()) {
            sb.append("\nCaveats:\n");
            for (String caveat : stats.caveats()) {
                sb.append("  - ").append(caveat).append("\n");
            }
        }

        if (!verdict.covariates().aligned()) {
            sb.append("\nCovariate misalignments:\n");
            if (!verdict.covariates().baselineProfile().isEmpty()) {
                sb.append("  Baseline:  ").append(formatProfile(verdict.covariates().baselineProfile())).append("\n");
                sb.append("  Observed:  ").append(formatProfile(verdict.covariates().observedProfile())).append("\n");
                sb.append("  Differs:\n");
            }
            for (Misalignment m : verdict.covariates().misalignments()) {
                sb.append(String.format("  %s: baseline=%s, test=%s\n",
                        m.covariateKey(), m.baselineValue(), m.testValue()));
            }
        }

        return sb.toString();
    }

    private static String formatProfile(Map<String, String> profile) {
        return profile.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    // ── Tooltips for HTML report ────────────────────────────────────────

    private static final Map<String, String> TOOLTIPS = Map.ofEntries(
            Map.entry("Baseline spec:", "The experiment spec from which the threshold was derived"),
            Map.entry("Baseline samples:", "Number of samples in the baseline experiment"),
            Map.entry("Derived threshold:", "Minimum pass rate derived from the baseline"),
            Map.entry("Confidence level:", "Probability that the CI method captures the true rate"),
            Map.entry("SE(p\u0302):", "Standard error of the observed proportion — measures sampling noise in p\u0302"),
            Map.entry("Wilson lower bound:", "One-sided Wilson lower bound on the true pass rate — we are this confident the true rate is at least this value"),
            Map.entry("Z:", "How many standard errors p\u0302 is from the threshold \u03C0\u2080 — negative means below"),
            Map.entry("p-value:", "Probability of seeing a rate this low or lower if the system truly meets the threshold — small = evidence of degradation"),
            Map.entry("Threshold derivation:", "Method used to derive the threshold from baseline data"),
            Map.entry("Latency assertions:", "Per-percentile latency checks against configured thresholds"),
            Map.entry("Covariate misalignments:", "Test conditions that differ from the baseline — may reduce comparability")
    );

    /**
     * Renders the statistical analysis as HTML with tooltip spans on labels.
     *
     * <p>Each label that matches a known statistical term is wrapped in a
     * {@code <span class="tip" data-tip="...">} element. A CSS {@code :hover::after}
     * rule renders the tooltip — no JavaScript required. Only the label is styled,
     * not the full line. The returned string is already HTML-safe — do not escape it.
     */
    public static String renderStatisticalAnalysisHtml(ProbabilisticTestVerdict verdict) {
        String plain = renderStatisticalAnalysis(verdict);
        StringBuilder html = new StringBuilder();
        for (String line : plain.split("\n", -1)) {
            String trimmed = line.trim();
            boolean matched = false;
            for (Map.Entry<String, String> entry : TOOLTIPS.entrySet()) {
                if (trimmed.startsWith(entry.getKey())) {
                    int labelStart = line.indexOf(entry.getKey());
                    int labelEnd = labelStart + entry.getKey().length();
                    html.append(escapeHtml(line.substring(0, labelStart)))
                        .append("<span class=\"tip\" data-tip=\"")
                        .append(escapeAttr(entry.getValue()))
                        .append("\">")
                        .append(escapeHtml(entry.getKey()))
                        .append("</span>")
                        .append(escapeHtml(line.substring(labelEnd)))
                        .append("\n");
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                html.append(escapeHtml(line)).append("\n");
            }
        }
        return html.toString();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeAttr(String text) {
        return escapeHtml(text).replace("\"", "&quot;");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INSTANCE METHODS — verbose console (transparent stats)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Result of rendering containing title and body for PUnitReporter.
     */
    public record RenderResult(String title, String body) {}

    /**
     * Renders a verbose statistical explanation for the PUnitReporter.
     *
     * <p>Generates hypothesis framing, verdict interpretation, and caveats
     * as presentation logic directly from the verdict model.
     */
    public RenderResult renderForReporter(ProbabilisticTestVerdict verdict) {
        String testName = verdict.identity().className() + "." + verdict.identity().methodName();
        String title = "STATISTICAL ANALYSIS FOR: " + testName;
        StringBuilder sb = new StringBuilder();

        if (detailLevel == TransparentStatsConfig.DetailLevel.VERBOSE) {
            renderHypothesisSection(sb, verdict);
        }
        renderObservedDataSection(sb, verdict);
        renderBaselineReferenceSection(sb, verdict);
        if (detailLevel == TransparentStatsConfig.DetailLevel.VERBOSE) {
            renderInferenceSection(sb, verdict);
        }
        renderLatencyAnalysisSection(sb, verdict);
        renderVerdictSection(sb, verdict);
        renderProvenanceSection(sb, verdict);

        return new RenderResult(title, sb.toString().trim());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VERBOSE SECTION RENDERERS
    // ═══════════════════════════════════════════════════════════════════════════

    private void renderHypothesisSection(StringBuilder sb, ProbabilisticTestVerdict verdict) {
        ExecutionSummary exec = verdict.execution();
        double threshold = exec.minPassRate();
        boolean isSmoke = exec.intent() == TestIntent.SMOKE;
        String originName = verdict.provenance()
                .map(SpecProvenance::thresholdOriginName).orElse("UNSPECIFIED");

        HypothesisFraming framing = getHypothesisFraming(originName, isSmoke);

        String h0 = String.format("True success rate %s %s %s (%s)",
                StatisticalVocabulary.PI, StatisticalVocabulary.GEQ,
                RateFormat.format(threshold), framing.h0Text);
        String h1 = String.format("True success rate %s < %s (%s)",
                StatisticalVocabulary.PI, RateFormat.format(threshold), framing.h1Text);

        sb.append("HYPOTHESIS TEST\n");
        sb.append(statLabel(symbols.h0() + " (null):", h0));
        sb.append(statLabel(symbols.h1() + " (alternative):", h1));
        sb.append(statLabel("Test type:", "One-sided binomial proportion test"));
        sb.append("\n");
    }

    private void renderObservedDataSection(StringBuilder sb, ProbabilisticTestVerdict verdict) {
        ExecutionSummary exec = verdict.execution();
        sb.append("OBSERVED DATA\n");
        sb.append(statLabel("Sample size (n):", String.valueOf(exec.samplesExecuted())));
        sb.append(statLabel("Successes (k):", String.valueOf(exec.successes())));
        sb.append(statLabel("Observed rate (" + symbols.pHat() + "):",
                RateFormat.format(exec.observedPassRate())));
        if (exec.warmup() > 0) {
            String serviceContractLabel = verdict.identity().serviceContractId().orElse("");
            sb.append(statLabel("Warmup:",
                    String.format("%d invocations discarded%s",
                            exec.warmup(),
                            serviceContractLabel.isEmpty() ? "" : " (" + serviceContractLabel + ")")));
        }
        sb.append("\n");
    }

    private void renderBaselineReferenceSection(StringBuilder sb, ProbabilisticTestVerdict verdict) {
        StatisticalAnalysis stats = verdict.statistics();
        ExecutionSummary exec = verdict.execution();

        sb.append("BASELINE REFERENCE\n");

        Optional<BaselineSummary> baselineOpt = stats.baseline();
        if (baselineOpt.isPresent()) {
            BaselineSummary baseline = baselineOpt.get();
            String dateStr = baseline.generatedAt() != null
                    ? DATE_FORMAT.format(baseline.generatedAt())
                    : "unknown";
            sb.append(statLabel("Source:", baseline.sourceFile() + " (generated " + dateStr + ")"));
            sb.append(statLabel("Empirical basis:",
                    String.format("%d samples, %d successes (%s)",
                            baseline.baselineSamples(), baseline.baselineSuccesses(),
                            RateFormat.format(baseline.baselineRate()))));
            stats.thresholdDerivation().ifPresent(d ->
                    sb.append(statLabel("Threshold derivation:", d)));
        } else {
            sb.append(statLabel("Source:", "(inline threshold)"));
            sb.append(statLabel("Threshold:",
                    String.format("%s (%s)", RateFormat.format(exec.minPassRate()),
                            "explicit minPassRate")));
        }
        sb.append("\n");
    }

    private void renderInferenceSection(StringBuilder sb, ProbabilisticTestVerdict verdict) {
        StatisticalAnalysis stats = verdict.statistics();
        ExecutionSummary exec = verdict.execution();

        double pHat = exec.observedPassRate();
        int n = exec.samplesExecuted();
        double pi0 = exec.minPassRate();

        sb.append("STATISTICAL INFERENCE\n");

        // SE with formula
        sb.append(statLabel("SE(" + symbols.pHat() + "):",
                String.format("SE = %s(%s(1-%s)/n) = %s(%.2f %s %.2f / %d) = %.4f",
                        symbols.sqrt(), symbols.pHat(), symbols.pHat(),
                        symbols.sqrt(), pHat, symbols.times(), (1 - pHat), n,
                        stats.standardError())));

        // One-sided Wilson lower bound (the verdict path is left-tailed —
        // an upper bound carries no operational meaning).
        sb.append(statLabel("Wilson lower bound:",
                String.format("%.0f%% one-sided lower = %.3f",
                        stats.confidenceLevel() * 100, stats.wilsonLower())));

        renderZTestCalculation(sb, verdict);

        sb.append("\n");
    }

    private void renderZTestCalculation(StringBuilder sb, ProbabilisticTestVerdict verdict) {
        StatisticalAnalysis stats = verdict.statistics();
        ExecutionSummary exec = verdict.execution();

        if (stats.testStatistic().isEmpty()) {
            return;
        }

        double pHat = exec.observedPassRate();
        double pi0 = exec.minPassRate();
        int n = exec.samplesExecuted();
        double z = stats.testStatistic().get();

        String valueIndent = " ".repeat(PUnitReporter.DETAIL_LABEL_WIDTH);
        sb.append("\n");
        sb.append(statLabel("Z:",
                String.format("z = (%s - %s%s) / %s(%s%s(1-%s%s)/n)",
                        symbols.pHat(), symbols.pi(), StatisticalVocabulary.SUB_ZERO,
                        symbols.sqrt(), symbols.pi(), StatisticalVocabulary.SUB_ZERO,
                        symbols.pi(), StatisticalVocabulary.SUB_ZERO)));
        sb.append(String.format("  %sz = (%.2f - %.2f) / %s(%.2f %s %.2f / %d)%n",
                valueIndent, pHat, pi0, symbols.sqrt(), pi0, symbols.times(), (1 - pi0), n));
        sb.append(String.format("  %sz = %.2f%n", valueIndent, z));

        stats.pValue().ifPresent(p -> {
            sb.append("\n");
            sb.append(statLabel("p-value:",
                    String.format("P(Z %s %.2f) = %.3f", symbols.leq(), z, p)));
        });
    }

    private void renderLatencyAnalysisSection(StringBuilder sb, ProbabilisticTestVerdict verdict) {
        if (verdict.latency().isEmpty()) {
            return;
        }

        LatencyDimension lat = verdict.latency().get();
        if (lat.skipped() && lat.successfulSamples() == 0) {
            // Only render for skipped-with-zero-successes case
            sb.append("LATENCY ANALYSIS\n");
            sb.append(statLabel("Population:",
                    String.format("No successful samples available (%d of %d)",
                            lat.successfulSamples(), lat.totalSamples())));
            String wrapIndent = "  " + " ".repeat(PUnitReporter.DETAIL_LABEL_WIDTH);
            int wrapIndentLen = wrapIndent.length();
            appendWrappedLabel(sb, "Latency:",
                    "Not evaluated. Latency assertions require at least one successful sample. " +
                    "The pass rate failure should be investigated first.",
                    wrapIndent, wrapIndentLen);
            sb.append("\n");
            return;
        }

        if (lat.skipped()) {
            return;
        }

        sb.append("LATENCY ANALYSIS\n");
        sb.append(statLabel("Population:",
                String.format("Successful samples only (n=%d of %d)",
                        lat.successfulSamples(), lat.totalSamples())));

        // Observed distribution
        sb.append("  Observed distribution:\n");
        if (isPercentileAvailable(lat.p50Ms())) sb.append(statLabel("p50:", lat.p50Ms() + "ms"));
        if (isPercentileAvailable(lat.p90Ms())) sb.append(statLabel("p90:", lat.p90Ms() + "ms"));
        if (isPercentileAvailable(lat.p95Ms())) sb.append(statLabel("p95:", lat.p95Ms() + "ms"));
        if (isPercentileAvailable(lat.p99Ms())) sb.append(statLabel("p99:", lat.p99Ms() + "ms"));
        if (isPercentileAvailable(lat.maxMs())) sb.append(statLabel("max:", lat.maxMs() + "ms"));
        sb.append("\n");

        // Latency caveats
        if (!lat.caveats().isEmpty()) {
            String wrapIndent = "  " + " ".repeat(PUnitReporter.DETAIL_LABEL_WIDTH);
            int wrapIndentLen = wrapIndent.length();
            for (String caveat : lat.caveats()) {
                appendWrappedLabel(sb, "Caveat:", caveat, wrapIndent, wrapIndentLen);
            }
            sb.append("\n");
        }
    }

    private void renderVerdictSection(StringBuilder sb, ProbabilisticTestVerdict verdict) {
        ExecutionSummary exec = verdict.execution();
        boolean passed = verdict.junitPassed();
        String technicalResult = passed ? "PASS" : "FAIL";

        sb.append("VERDICT\n");
        sb.append(statLabel("Result:", technicalResult));

        // Generate plain English interpretation
        String plainEnglish = buildVerdictInterpretation(verdict);

        String wrapIndent = "  " + " ".repeat(PUnitReporter.DETAIL_LABEL_WIDTH);
        int wrapIndentLen = wrapIndent.length();
        appendWrappedLabel(sb, "Interpretation:", plainEnglish, wrapIndent, wrapIndentLen);

        // Generate verbose caveats
        List<String> caveats = buildVerboseCaveats(verdict);
        if (!caveats.isEmpty()) {
            sb.append("\n");
            for (String caveat : caveats) {
                appendWrappedLabel(sb, "Caveat:", caveat, wrapIndent, wrapIndentLen);
            }
        }

        sb.append("\n");
    }

    private void renderProvenanceSection(StringBuilder sb, ProbabilisticTestVerdict verdict) {
        if (verdict.provenance().isEmpty()) {
            return;
        }

        SpecProvenance prov = verdict.provenance().get();
        String originName = prov.thresholdOriginName();
        boolean hasOrigin = originName != null && !originName.isEmpty()
                && !"UNSPECIFIED".equals(originName);
        boolean hasContract = prov.contractRef() != null && !prov.contractRef().isEmpty();

        if (!hasOrigin && !hasContract) {
            return;
        }

        sb.append("THRESHOLD PROVENANCE\n");
        if (hasOrigin) {
            sb.append(statLabel("Threshold origin:", originName));
        }
        if (hasContract) {
            sb.append(statLabel("Contract:", prov.contractRef()));
        }
        sb.append("\n");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRESENTATION LOGIC — hypothesis, verdict, caveats
    // ═══════════════════════════════════════════════════════════════════════════

    private String buildVerdictInterpretation(ProbabilisticTestVerdict verdict) {
        ExecutionSummary exec = verdict.execution();
        boolean passed = verdict.junitPassed();
        boolean isSmoke = exec.intent() == TestIntent.SMOKE;
        String originName = verdict.provenance()
                .map(SpecProvenance::thresholdOriginName).orElse("UNSPECIFIED");

        VerdictFraming framing = getVerdictFraming(originName, isSmoke);

        if (passed) {
            if (verdict.statistics().baseline().isPresent()) {
                return String.format(
                        "The observed success rate of %s is consistent with the baseline expectation of %s. %s",
                        RateFormat.format(exec.observedPassRate()),
                        RateFormat.format(verdict.statistics().baseline().get().baselineRate()),
                        framing.passText);
            } else {
                return String.format(
                        "The observed success rate of %s meets the required threshold of %s. %s",
                        RateFormat.format(exec.observedPassRate()),
                        RateFormat.format(exec.minPassRate()),
                        framing.passText);
            }
        } else {
            return String.format(
                    "The observed success rate of %s falls below the required threshold of %s. %s",
                    RateFormat.format(exec.observedPassRate()),
                    RateFormat.format(exec.minPassRate()),
                    framing.failText);
        }
    }

    private List<String> buildVerboseCaveats(ProbabilisticTestVerdict verdict) {
        ExecutionSummary exec = verdict.execution();
        int samples = exec.samplesExecuted();
        double observedRate = exec.observedPassRate();
        double threshold = exec.minPassRate();
        boolean isSmoke = exec.intent() == TestIntent.SMOKE;
        double confidenceLevel = exec.resolvedConfidence();
        String originName = verdict.provenance()
                .map(SpecProvenance::thresholdOriginName).orElse("UNSPECIFIED");
        String contractRef = verdict.provenance()
                .map(SpecProvenance::contractRef).orElse("");

        List<String> caveats = new ArrayList<>();

        // Covariate misalignment caveat
        if (!verdict.covariates().aligned()) {
            StringBuilder msb = new StringBuilder();
            msb.append("Covariate misalignment detected: the test conditions differ from the baseline. ");
            msb.append("Misaligned covariates: ");
            List<Misalignment> misalignments = verdict.covariates().misalignments();
            for (int i = 0; i < misalignments.size(); i++) {
                if (i > 0) msb.append(", ");
                Misalignment m = misalignments.get(i);
                msb.append(m.covariateKey())
                   .append(" (baseline=").append(m.baselineValue())
                   .append(", test=").append(m.testValue()).append(")");
            }
            msb.append(". Statistical comparison may be less reliable.");
            caveats.add(msb.toString());
        }

        // Sample size caveats
        if (samples < 30) {
            caveats.add(String.format(
                    "Small sample size (n=%d). Statistical conclusions should be interpreted with caution. " +
                    "Consider increasing sample size for more reliable results.", samples));
        } else if (samples < 100) {
            caveats.add(String.format(
                    "With n=%d samples, subtle performance changes may not be detectable. " +
                    "For higher sensitivity, consider increasing sample size.", samples));
        } else {
            double margin = observedRate - threshold;
            if (margin > 0 && margin < 0.05) {
                caveats.add(String.format(
                        "The observed rate (%s) is close to the min pass rate (%s). " +
                        "Small fluctuations in future runs may cause different verdicts.",
                        RateFormat.format(observedRate), RateFormat.format(threshold)));
            }
        }

        // Edge case caveats
        if (observedRate == 1.0) {
            caveats.add("Perfect success rate observed. This may indicate insufficient test coverage " +
                    "or a test that doesn't adequately challenge the system.");
        } else if (observedRate == 0.0) {
            caveats.add("Zero success rate observed. This indicates a fundamental failure " +
                    "that may warrant investigation before further testing.");
        }

        // Inline threshold caveat
        if (verdict.statistics().baseline().isEmpty()) {
            if (originName.equalsIgnoreCase("UNSPECIFIED") || originName.equalsIgnoreCase("EMPIRICAL")) {
                caveats.add("Using inline threshold (no baseline spec). For statistically-derived " +
                        "thresholds with confidence intervals, run a MEASURE experiment first.");
            }
        }

        // Compliance evidence caveat
        if (ComplianceEvidenceEvaluator.hasComplianceContext(originName, contractRef)) {
            if (ComplianceEvidenceEvaluator.isUndersized(samples, threshold)) {
                caveats.add(String.format(
                        "Warning: %s. With n=%d and target of %s, even zero failures would " +
                        "not provide sufficient statistical evidence of compliance (\u03b1=%.3f). " +
                        "A PASS at this sample size is a smoke-test-level observation, not a compliance " +
                        "determination. Note: a FAIL verdict remains a reliable indication of non-conformance.",
                        ComplianceEvidenceEvaluator.SIZING_NOTE, samples, RateFormat.format(threshold),
                        ComplianceEvidenceEvaluator.DEFAULT_ALPHA));
            }
        }

        // Smoke intent sizing caveats
        if (isSmoke) {
            boolean isNormative = originName.equalsIgnoreCase("SLA")
                    || originName.equalsIgnoreCase("SLO")
                    || originName.equalsIgnoreCase("POLICY");
            if (isNormative && !Double.isNaN(threshold) && threshold > 0.0 && threshold < 1.0) {
                var result = VerificationFeasibilityEvaluator.evaluate(samples, threshold, confidenceLevel);
                if (!result.feasible()) {
                    caveats.add(String.format(
                            "Sample not sized for verification (N=%d, need %d). " +
                            "This is a smoke-test-level observation, not a compliance determination.",
                            samples, result.minimumSamples()));
                } else {
                    caveats.add("Sample is sized for verification. " +
                            "Consider setting intent = VERIFICATION for stronger statistical guarantees.");
                }
            }
        }

        return caveats;
    }

    private record HypothesisFraming(String h0Text, String h1Text) {}

    private HypothesisFraming getHypothesisFraming(String originName, boolean isSmoke) {
        if (originName == null) originName = "UNSPECIFIED";

        if (isSmoke) {
            return switch (originName.toUpperCase()) {
                case "SLA", "SLO", "POLICY" -> new HypothesisFraming(
                        "observed rate consistent with target",
                        "observed rate inconsistent with target");
                case "EMPIRICAL" -> new HypothesisFraming(
                        "no degradation from baseline",
                        "degradation from baseline");
                default -> new HypothesisFraming(
                        "observed rate meets threshold",
                        "observed rate below threshold");
            };
        }

        return switch (originName.toUpperCase()) {
            case "SLA" -> new HypothesisFraming(
                    "system meets SLA requirement",
                    "system violates SLA");
            case "SLO" -> new HypothesisFraming(
                    "system meets SLO target",
                    "system falls short of SLO");
            case "POLICY" -> new HypothesisFraming(
                    "system meets policy requirement",
                    "system violates policy");
            case "EMPIRICAL" -> new HypothesisFraming(
                    "no degradation from baseline",
                    "degradation from baseline");
            default -> new HypothesisFraming(
                    "success rate meets threshold",
                    "success rate below threshold");
        };
    }

    private record VerdictFraming(String passText, String failText) {}

    private VerdictFraming getVerdictFraming(String originName, boolean isSmoke) {
        if (originName == null) originName = "UNSPECIFIED";

        if (isSmoke) {
            return switch (originName.toUpperCase()) {
                case "SLA", "SLO", "POLICY" -> new VerdictFraming(
                        "The observed rate is consistent with the target.",
                        "The observed rate is inconsistent with the target.");
                case "EMPIRICAL" -> new VerdictFraming(
                        "No degradation from baseline detected.",
                        "This suggests potential degradation from the established baseline.");
                default -> new VerdictFraming(
                        "The test passes.",
                        "The observed rate does not meet the threshold.");
            };
        }

        return switch (originName.toUpperCase()) {
            case "SLA" -> new VerdictFraming(
                    "The system meets its SLA requirement.",
                    "This indicates the system is not meeting its SLA obligation.");
            case "SLO" -> new VerdictFraming(
                    "The system meets its SLO target.",
                    "This indicates the system is falling short of its SLO target.");
            case "POLICY" -> new VerdictFraming(
                    "The system meets the policy requirement.",
                    "This indicates a policy violation.");
            case "EMPIRICAL" -> new VerdictFraming(
                    "No degradation from baseline detected.",
                    "This suggests potential degradation from the established baseline.");
            default -> new VerdictFraming(
                    "The test passes.",
                    "This suggests the system is not meeting its expected performance level.");
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    private String statLabel(String label, String value) {
        return "  " + PUnitReporter.labelValueLn(label, value, PUnitReporter.DETAIL_LABEL_WIDTH);
    }

    private void appendWrappedLabel(StringBuilder sb, String label, String text,
            String wrapIndent, int wrapIndentLen) {
        sb.append("  ").append(String.format("%-" + PUnitReporter.DETAIL_LABEL_WIDTH + "s", label));
        String[] words = text.split(" ");
        int lineLength = wrapIndentLen;
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (lineLength + word.length() + 1 > LINE_WIDTH && !line.isEmpty()) {
                sb.append(line.toString().trim()).append("\n");
                sb.append(wrapIndent);
                line = new StringBuilder();
                lineLength = wrapIndentLen;
            }
            line.append(word).append(" ");
            lineLength += word.length() + 1;
        }
        if (!line.isEmpty()) {
            sb.append(line.toString().trim()).append("\n");
        }
    }

    /**
     * Formats the termination message with contextual execution data.
     *
     * <p>Budget exhaustion reasons are enriched with sample counts so the reader can
     * distinguish resource starvation from genuine underperformance at a glance.
     * This is the single formatting path for all termination messages across
     * both summary (HTML report) and console rendering.
     *
     * @param reason the termination reason
     * @param samplesExecuted the number of samples that were executed
     * @param plannedSamples the total number of samples that were planned
     * @return a contextual, human-readable termination message
     */
    public static String formatTerminationMessage(TerminationReason reason,
                                                    int samplesExecuted,
                                                    int plannedSamples) {
        if (reason.isBudgetExhaustion()) {
            return String.format("%s (%d/%d samples executed)",
                    reason.getDescription(), samplesExecuted, plannedSamples);
        }
        return reason.getDescription();
    }

    private static void appendBaselineLine(StringBuilder sb, ProbabilisticTestVerdict verdict) {
        verdict.provenance().ifPresent(prov -> {
            if (prov.specFilename() != null && !prov.specFilename().isEmpty()) {
                String sourceLabel = prov.baselineSourceLabel().orElse("");
                String value = sourceLabel.isEmpty()
                        ? prov.specFilename()
                        : prov.specFilename() + " " + sourceLabel;
                sb.append(PUnitReporter.labelValue("Baseline:", value));
            }
        });
    }

    /**
     * Format a percentile's milliseconds value, rendering the
     * {@link LatencySection#PERCENTILE_UNAVAILABLE_MS} sentinel as
     * {@code "-"} (contributingSamples below this percentile's
     * minimum threshold means the value is not reliably estimated).
     */
    private static String formatMsOrDash(long ms) {
        return isPercentileAvailable(ms) ? ms + "ms" : "-";
    }

    private static boolean isPercentileAvailable(long ms) {
        return ms != LatencySection.PERCENTILE_UNAVAILABLE_MS;
    }

    private static void appendDimensionBreakdown(StringBuilder sb, ProbabilisticTestVerdict verdict) {
        if (verdict.functional().isEmpty()) {
            return;
        }
        FunctionalDimension func = verdict.functional().get();
        sb.append(PUnitReporter.labelValueLn("Contract:",
                String.format("%d/%d passed",
                        func.successes(), func.successes() + func.failures())));

        verdict.latency().ifPresent(lat -> {
            // Descriptive one-liner: surfaces the passing-only
            // percentiles + indicator counts alongside the pass/fail
            // block so a reader sees latency at a glance, independent
            // of latency-assertion activation. Percentiles below the
            // minimum-samples threshold render as "-" rather than a
            // misleading number.
            if (lat.successfulSamples() > 0) {
                sb.append(PUnitReporter.labelValueLn("Latency:",
                        String.format("(%d/%d passing) p50=%s p95=%s p99=%s",
                                lat.successfulSamples(), lat.totalSamples(),
                                formatMsOrDash(lat.p50Ms()),
                                formatMsOrDash(lat.p95Ms()),
                                formatMsOrDash(lat.p99Ms()))));
            }
        });
    }
}
