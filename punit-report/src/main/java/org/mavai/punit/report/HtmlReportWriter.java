package org.mavai.punit.report;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mavai.punit.api.spec.FailureCount;
import org.mavai.punit.api.spec.FailureExemplar;
import org.mavai.punit.api.spec.Verdict;
import org.mavai.punit.internal.engine.emit.LatencySection;
import org.mavai.punit.verdict.CriterionRow;
import org.mavai.punit.verdict.PerCriterionStructure;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.ExecutionSummary;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.FunctionalDimension;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.LatencyDimension;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.Misalignment;
import org.mavai.punit.verdict.PUnitVerdict;
import org.mavai.punit.internal.reporting.VerdictTextRenderer;

/**
 * Generates a standalone HTML report from a list of {@link ProbabilisticTestVerdict}s.
 *
 * <p>The report is a single HTML file with embedded CSS — no external dependencies.
 * Test results are grouped by use-case-id (or class name when absent) and presented
 * in an expandable table using HTML5 {@code <details>}/{@code <summary>} elements.
 */
// javai-ref: JVI-PNR8C3F — do not remove (resolves in javai-orchestrator)
final class HtmlReportWriter {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
                    .withZone(ZoneId.systemDefault());

    private HtmlReportWriter() {
    }

    /**
     * Generates the HTML report content.
     *
     * @param verdicts the list of verdicts to include
     * @return the complete HTML content
     */
    static String generate(List<ProbabilisticTestVerdict> verdicts) {
        Instant now = Instant.now();
        long totalTests = verdicts.size();
        long passed = verdicts.stream().filter(v -> v.punitVerdict() == PUnitVerdict.PASS).count();
        long failed = verdicts.stream().filter(v -> v.punitVerdict() == PUnitVerdict.FAIL).count();
        long inconclusive = verdicts.stream().filter(v -> v.punitVerdict() == PUnitVerdict.INCONCLUSIVE).count();

        Map<String, List<ProbabilisticTestVerdict>> grouped = groupVerdicts(verdicts);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("<title>PUnit Test Report</title>\n");
        appendCss(html);
        html.append("</head>\n<body>\n");

        // Header
        html.append("<header>\n");
        html.append("<h1>PUnit Test Report</h1>\n");
        html.append("<p class=\"timestamp\">Generated: ").append(escape(TIMESTAMP_FORMAT.format(now))).append("</p>\n");
        html.append("<div class=\"summary-stats\">\n");
        html.append("<span class=\"stat\">Total: ").append(totalTests).append("</span>\n");
        html.append("<span class=\"stat punit-pass\">Pass: ").append(passed).append("</span>\n");
        html.append("<span class=\"stat punit-fail\">Fail: ").append(failed).append("</span>\n");
        if (inconclusive > 0) {
            html.append("<span class=\"stat punit-inconclusive\">Inconclusive: ").append(inconclusive).append("</span>\n");
        }
        html.append("</div>\n");
        if (inconclusive > 0) {
            html.append("<div class=\"banner-inconclusive\">\n");
            html.append("<strong>Attention:</strong> ");
            html.append(inconclusive).append(inconclusive == 1 ? " test has" : " tests have");
            html.append(" an inconclusive verdict due to baseline misalignment. ");
            html.append("Re-run experiments to produce baselines matching the current environment: ");
            html.append("<code>./gradlew exp</code>");
            html.append("\n</div>\n");
        }
        html.append("</header>\n");

        // Statistical assumptions (collapsed by default)
        appendStatisticalAssumptions(html);

        // Results table
        html.append("<main>\n");

        for (Map.Entry<String, List<ProbabilisticTestVerdict>> group : grouped.entrySet()) {
            html.append("<section class=\"test-group\">\n");
            html.append("<h2>").append(escape(group.getKey())).append("</h2>\n");
            html.append("<table>\n");
            html.append("<thead>\n<tr>\n");
            html.append("<th>Test Name</th>");
            html.append("<th>Verdict</th>");
            html.append("<th>Functional</th>");
            html.append("<th>p50</th>");
            html.append("<th>p95</th>");
            html.append("<th>p99</th>");
            html.append("<th>Samples</th>");
            html.append("<th>Elapsed</th>");
            html.append("</tr>\n</thead>\n<tbody>\n");

            for (ProbabilisticTestVerdict verdict : group.getValue()) {
                appendVerdictRow(html, verdict);
            }

            html.append("</tbody>\n</table>\n</section>\n");
        }

        html.append("</main>\n");

        // Footer
        html.append("<footer>\n");
        html.append("<p>Generated by PUnit at ").append(escape(TIMESTAMP_FORMAT.format(now))).append("</p>\n");
        html.append("</footer>\n");
        html.append("</body>\n</html>\n");

        return html.toString();
    }

    private static void appendStatisticalAssumptions(StringBuilder html) {
        html.append("<details class=\"assumptions\">\n");
        html.append("<summary>Statistical assumptions and limitations</summary>\n");
        html.append("<div class=\"assumptions-body\">\n");
        html.append("<p>This report uses statistical methods that assume repeated executions can be treated ");
        html.append("as comparable pass/fail trials. That is not automatically true for every test. ");
        html.append("If the test itself changes the state, performance, or behaviour of the system ");
        html.append("from one run to the next, the resulting figures may be mathematically correct ");
        html.append("yet statistically misleading. In such cases, the report should be read as a ");
        html.append("rough signal only, not as a reliable probabilistic assessment.</p>\n");
        html.append("<p>The statistics in this report are valid when the following assumptions hold:</p>\n");
        html.append("<ul>\n");
        html.append("<li><strong>Binary outcome</strong> &mdash; each run has a clear and consistent pass/fail result.</li>\n");
        html.append("<li><strong>Same question each time</strong> &mdash; repeated runs are testing the same condition.</li>\n");
        html.append("<li><strong>Unchanged threshold</strong> &mdash; the success criterion remains the same throughout.</li>\n");
        html.append("<li><strong>Independence</strong> &mdash; earlier runs do not substantially influence later ones.</li>\n");
        html.append("<li><strong>No major drift during sampling</strong> &mdash; the underlying behaviour is reasonably ");
        html.append("stable over the sample window.</li>\n");
        html.append("</ul>\n");
        html.append("<p class=\"assumptions-warning\"><strong>Warning:</strong> tests that warm up, exhaust, mutate, ");
        html.append("learn, cache, throttle, or degrade the target can violate these assumptions and ");
        html.append("weaken the meaning of the statistics.</p>\n");
        html.append("</div>\n");
        html.append("</details>\n");
    }

    private static void appendVerdictRow(StringBuilder html, ProbabilisticTestVerdict verdict) {
        ExecutionSummary exec = verdict.execution();
        String methodName = verdict.identity().methodName();
        String punitClass = punitCssClass(verdict.punitVerdict());

        html.append("<tr>\n");
        html.append("<td>\n");
        html.append("<details>\n");
        html.append("<summary>").append(escape(methodName)).append("</summary>\n");

        // Level 2: summary text
        html.append("<pre class=\"level2\">").append(escape(VerdictTextRenderer.renderSummary(verdict))).append("</pre>\n");

        // Operator guidance for inconclusive verdicts
        if (verdict.punitVerdict() == PUnitVerdict.INCONCLUSIVE && !verdict.covariates().aligned()) {
            appendMisalignmentGuidance(html, verdict);
        }

        // Run design: the sizing-transparency disclosures
        appendRunDesign(html, verdict);

        // Level 3: full statistical analysis (nested details)
        html.append("<details>\n");
        html.append("<summary>Statistical Analysis</summary>\n");
        html.append("<pre class=\"level3\">").append(VerdictTextRenderer.renderStatisticalAnalysisHtml(verdict)).append("</pre>\n");
        html.append("</details>\n");

        // Level 3: methodology-level per-criterion breakdown (sibling to Statistical Analysis)
        appendPerCriterionBreakdown(html, verdict);

        // Per-clause failure histogram (only when non-empty)
        appendPostconditionFailures(html, verdict.postconditionFailures());

        html.append("</details>\n");
        html.append("</td>\n");

        // Verdict (single column; in 0.7.0 the test body always represents
        // one statistical test, so a JUnit pass/fail cannot diverge from
        // the punit verdict — the JUnit column would carry no extra
        // signal).
        html.append("<td class=\"").append(punitClass).append("\">")
                .append(verdict.punitVerdict().name()).append("</td>\n");

        // Functional
        if (verdict.functional().isPresent()) {
            FunctionalDimension func = verdict.functional().get();
            html.append("<td>").append(func.successes()).append("/")
                    .append(func.successes() + func.failures()).append("</td>\n");
        } else {
            html.append("<td>-</td>\n");
        }

        // Latency (p50, p95, p99)
        if (verdict.latency().isPresent() && !verdict.latency().get().skipped()) {
            LatencyDimension lat = verdict.latency().get();
            appendLatencyCell(html, lat, "p50", lat.p50Ms());
            appendLatencyCell(html, lat, "p95", lat.p95Ms());
            appendLatencyCell(html, lat, "p99", lat.p99Ms());
        } else {
            html.append("<td>-</td>\n<td>-</td>\n<td>-</td>\n");
        }

        // Samples
        html.append("<td>").append(exec.samplesExecuted()).append("/")
                .append(exec.plannedSamples()).append("</td>\n");

        // Elapsed
        html.append("<td>").append(exec.elapsedMs()).append("ms</td>\n");

        html.append("</tr>\n");
    }

    private static void appendMisalignmentGuidance(StringBuilder html, ProbabilisticTestVerdict verdict) {
        html.append("<div class=\"misalignment-guidance\">\n");
        html.append("<strong>Covariate misalignment</strong> — baselines do not match the current environment.<br>\n");
        for (Misalignment m : verdict.covariates().misalignments()) {
            html.append(escape(m.covariateKey())).append(": baseline=<em>")
                    .append(escape(m.baselineValue())).append("</em>, test=<em>")
                    .append(escape(m.testValue())).append("</em><br>\n");
        }
        String className = verdict.identity().className();
        String simpleClassName = className.contains(".")
                ? className.substring(className.lastIndexOf('.') + 1)
                : className;
        html.append("Re-run experiment to regenerate baseline: <code>./gradlew exp -Prun=")
                .append(escape(simpleClassName)).append("</code>\n");
        html.append("</div>\n");
    }

    /**
     * Renders the per-clause failure histogram as a nested {@code <details>}
     * block. Clauses appear in declaration order. Each row shows the clause
     * description, total count, and any retained exemplars. Omitted entirely
     * when the histogram is empty (clean run, or contract with no clauses).
     */
    private static void appendPostconditionFailures(
            StringBuilder html, Map<String, FailureCount> byClause) {
        if (byClause == null || byClause.isEmpty()) {
            return;
        }
        html.append("<details>\n");
        html.append("<summary>Postcondition Failures</summary>\n");
        html.append("<table class=\"postcondition-failures\">\n");
        html.append("<thead><tr><th>Clause</th><th>Count</th><th>Exemplars</th></tr></thead>\n");
        html.append("<tbody>\n");
        for (Map.Entry<String, FailureCount> entry : byClause.entrySet()) {
            FailureCount bucket = entry.getValue();
            html.append("<tr>\n");
            html.append("<td class=\"clause\">").append(escape(entry.getKey())).append("</td>\n");
            html.append("<td class=\"count\">").append(bucket.count()).append("</td>\n");
            html.append("<td class=\"exemplars\">");
            if (bucket.exemplars().isEmpty()) {
                html.append("<span class=\"no-exemplars\">(no exemplars retained)</span>");
            } else {
                html.append("<ul>\n");
                for (FailureExemplar ex : bucket.exemplars()) {
                    html.append("<li><code>").append(escape(ex.input()))
                            .append("</code> &rarr; ").append(escape(ex.reason()))
                            .append("</li>\n");
                }
                html.append("</ul>");
            }
            html.append("</td>\n");
            html.append("</tr>\n");
        }
        html.append("</tbody>\n");
        html.append("</table>\n");
        html.append("</details>\n");
    }

    private static void appendLatencyCell(StringBuilder html, LatencyDimension lat,
                                             String label, long observedMs) {
        if (observedMs == LatencySection.PERCENTILE_UNAVAILABLE_MS) {
            // Below the minimum-samples threshold for this
            // percentile - render as a dash rather than the literal
            // sentinel value or a misleading number.
            html.append("<td class=\"latency-observed\">-</td>\n");
            return;
        }
        html.append("<td class=\"latency-observed\">").append(observedMs).append("ms</td>\n");
    }

    private static Map<String, List<ProbabilisticTestVerdict>> groupVerdicts(
            List<ProbabilisticTestVerdict> verdicts) {
        Map<String, List<ProbabilisticTestVerdict>> grouped = new LinkedHashMap<>();
        for (ProbabilisticTestVerdict verdict : verdicts) {
            String key = verdict.identity().serviceContractId()
                    .orElse(verdict.identity().className());
            grouped.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(verdict);
        }
        return grouped;
    }

    private static String punitCssClass(PUnitVerdict verdict) {
        return switch (verdict) {
            case PASS -> "punit-pass";
            case FAIL -> "punit-fail";
            case INCONCLUSIVE -> "punit-inconclusive";
        };
    }

    private static String verdictCssClass(Verdict verdict) {
        return switch (verdict) {
            case PASS -> "punit-pass";
            case FAIL -> "punit-fail";
            case INCONCLUSIVE -> "punit-inconclusive";
        };
    }

    private static String formatRate(double value) {
        if (Double.isNaN(value)) {
            return "&mdash;";
        }
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    /**
     * Renders the methodology-level per-criterion breakdown as a
     * nested {@code <details>} block at the deepest expansion level.
     * Each row appears as its own group, headed by the criterion's
     * identifier (no separate "Criterion" label — the id is the
     * heading). The latency criterion is intentionally absent here:
     * latency is surfaced once, in the dedicated p50 / p95 / p99
     * columns at level 1 and the Statistical Analysis block at level
     * 3. The block is omitted entirely when no per-criterion
     * structure is present (legacy 1.0 XML, apply-level-failure
     * runs).
     */
    private static void appendPerCriterionBreakdown(
            StringBuilder html, ProbabilisticTestVerdict verdict) {
        Optional<PerCriterionStructure> opt = verdict.perCriterion();
        if (opt.isEmpty() || opt.get().criteria().isEmpty()) {
            return;
        }
        PerCriterionStructure pc = opt.get();
        html.append("<details>\n");
        html.append("<summary>Per-criterion breakdown</summary>\n");
        html.append("<div class=\"per-criterion\">\n");
        for (CriterionRow row : pc.criteria()) {
            html.append("<div class=\"criterion-block\">\n");
            html.append("<h4>").append(escape(row.criterionId()))
                    .append(" <span class=\"").append(verdictCssClass(row.verdict()))
                    .append("\">").append(row.verdict().name()).append("</span></h4>\n");
            html.append("<dl>\n");
            html.append("<dt>Pass</dt><dd>").append(row.pass()).append("</dd>\n");
            html.append("<dt>Fail</dt><dd>").append(row.fail()).append("</dd>\n");
            html.append("<dt>Inconclusive</dt><dd>").append(row.inconclusive()).append("</dd>\n");
            html.append("<dt>Total</dt><dd>").append(row.total()).append("</dd>\n");
            html.append("<dt>Observed rate</dt><dd>").append(formatRate(row.observedRate())).append("</dd>\n");
            html.append("<dt>Threshold</dt><dd>").append(formatRate(row.threshold())).append("</dd>\n");
            html.append("</dl>\n");
            html.append("</div>\n");
        }
        html.append("</div>\n");
        html.append("</details>\n");
    }

    /**
     * The canonical operational-approach glosses, in the plain register.
     * The confidence-first entries are rendered when a run's configuration
     * declares that approach — the vocabulary is ready ahead of the
     * authoring surface that will produce it.
     */
    private static final Map<String, String> APPROACH_GLOSSES = Map.of(
            "sample-size-first",
            "the sample size was chosen first; the acceptance bar was derived honestly at that size",
            "threshold-first",
            "the pass bar is externally stipulated; the run judges the evidence against it",
            "confidence-first",
            "the run size was computed from the declared confidence, detectable effect, and power",
            "confidence-first (risk-driven)",
            "the run size was computed from the declared tolerance and confidence, "
                    + "priced against the acceptance bar this very size derives");

    /**
     * The declared-parameter rows of the run-design block, in display
     * order: entry key, label, and whether the value renders as a
     * percentage. Rows whose entry is absent are skipped, so the block
     * extends additively as future approaches declare more parameters.
     */
    private static final String[][] RUN_DESIGN_FACTS = {
            {"sizing-declared-samples", "Declared samples", "plain"},
            {"sizing-tolerated-rate", "Tolerated rate", "percent"},
            {"sizing-declared-min-pass-rate", "Minimum pass rate", "percent"},
            {"sizing-declared-min-detectable-effect", "Minimum detectable effect", "plain"},
            {"sizing-declared-confidence", "Confidence", "percent"},
            {"sizing-declared-power", "Target power", "percent"},
            {"sizing-computed-samples", "Computed sample size", "plain"},
    };

    /**
     * Renders the run-design block: the operational approach that shaped
     * the run's size and — for a run sized below its baseline's own
     * measurement — the paired downsizing and efficiency disclosures.
     * All values arrive computed on the verdict's environment entries;
     * this renderer only formats. Omitted for records that carry no
     * sizing facts (verdicts predating the disclosures).
     */
    private static void appendRunDesign(StringBuilder html, ProbabilisticTestVerdict verdict) {
        Map<String, String> env = verdict.environmentMetadata();
        String approach = env.get("sizing-approach");
        if (approach == null) {
            return;
        }
        html.append("<details>\n<summary>Run design</summary>\n");
        html.append("<div class=\"per-criterion\">\n<div class=\"criterion-block\">\n");
        html.append("<p><strong>Approach:</strong> ").append(escape(approach));
        String gloss = APPROACH_GLOSSES.get(approach);
        if (gloss != null) {
            html.append(" &mdash; ").append(escape(gloss));
        }
        html.append("</p>\n<dl>\n");
        for (String[] fact : RUN_DESIGN_FACTS) {
            String value = env.get(fact[0]);
            if (value == null) {
                continue;
            }
            String rendered = "percent".equals(fact[2]) ? percent(value) : escape(value);
            html.append("<dt>").append(fact[1]).append("</dt><dd>")
                    .append(rendered).append("</dd>\n");
        }
        html.append("</dl>\n");
        appendSizingTrade(html, verdict, env);
        html.append("</div>\n</div>\n</details>\n");
    }

    /**
     * The downsizing disclosure and its paired efficiency estimate — one
     * trade, two sides, presented together. Present iff the verdict
     * carries the computed detectable rate and its baseline summary.
     */
    private static void appendSizingTrade(
            StringBuilder html, ProbabilisticTestVerdict verdict, Map<String, String> env) {
        String detectableRate = env.get("sizing-detectable-rate");
        var baseline = verdict.statistics().baseline();
        if (detectableRate == null || baseline.isEmpty()) {
            return;
        }
        int planned = verdict.execution().plannedSamples();
        int baselineSamples = baseline.get().baselineSamples();
        html.append("<p>This test was sized at ").append(planned)
                .append(" samples against a baseline measured over ").append(baselineSamples)
                .append(". With ").append(planned)
                .append(" samples, this test would only catch a drop below ")
                .append(percent(detectableRate)).append(' ')
                .append(powerPhrase(env.get("sizing-detectable-power"))).append(".</p>\n");

        String savedFraction = env.get("sizing-saved-fraction");
        String timeSavedMs = env.get("sizing-time-saved-ms");
        String tokensSaved = env.get("sizing-tokens-saved");
        html.append("<p>Estimated saving versus a run at the baseline's ")
                .append(baselineSamples).append(" samples: about ")
                .append(percent(savedFraction));
        if (tokensSaved != null) {
            html.append(" less execution time and tokens (roughly ")
                    .append(seconds(timeSavedMs)).append(" and ").append(escape(tokensSaved))
                    .append(" tokens, from this run's own per-sample averages). Estimates only.");
        } else {
            html.append(" less execution time (roughly ").append(seconds(timeSavedMs))
                    .append(", from this run's own per-sample average). Estimates only; ")
                    .append("no token figures are recorded for this run.");
        }
        html.append("</p>\n");
    }

    /** Plain language for the disclosed power; the default reads as odds. */
    private static String powerPhrase(String targetPower) {
        double power = Double.parseDouble(targetPower);
        if (Math.abs(power - 0.8) < 1e-9) {
            return "four times out of five";
        }
        return "about " + percent(targetPower) + " of the time";
    }

    private static String percent(String fraction) {
        return Math.round(Double.parseDouble(fraction) * 100) + "%";
    }

    private static String seconds(String milliseconds) {
        return String.format(java.util.Locale.ROOT, "%.1f seconds",
                Double.parseDouble(milliseconds) / 1000.0);
    }

    private static String escape(String text) {
        return ReportHtml.escape(text);
    }

    private static void appendCss(StringBuilder html) {
        ReportHtml.appendBaseCss(html);
    }
}
