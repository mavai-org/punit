package org.javai.punit.report;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.javai.punit.api.spec.FailureCount;
import org.javai.punit.api.spec.FailureExemplar;
import org.javai.punit.api.spec.Verdict;
import org.javai.punit.internal.engine.emit.LatencySection;
import org.javai.punit.verdict.CriterionRow;
import org.javai.punit.verdict.PerCriterionStructure;
import org.javai.punit.verdict.ProbabilisticTestVerdict;
import org.javai.punit.verdict.ProbabilisticTestVerdict.ExecutionSummary;
import org.javai.punit.verdict.ProbabilisticTestVerdict.FunctionalDimension;
import org.javai.punit.verdict.ProbabilisticTestVerdict.LatencyDimension;
import org.javai.punit.verdict.ProbabilisticTestVerdict.Misalignment;
import org.javai.punit.verdict.PUnitVerdict;
import org.javai.punit.internal.reporting.VerdictTextRenderer;

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

    private static String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static void appendCss(StringBuilder html) {
        html.append("<style>\n");
        html.append("""
                :root {
                    --pass-color: #2e7d32;
                    --fail-color: #c62828;
                    --inconclusive-color: #6a1b9a;
                    --border-color: #dee2e6;
                    --bg-light: #f8f9fa;
                    --bg-white: #ffffff;
                    --text-color: #212529;
                    --text-muted: #6c757d;
                }
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    color: var(--text-color);
                    background: var(--bg-light);
                    padding: 2rem;
                    max-width: 1200px;
                    margin: 0 auto;
                }
                header { margin-bottom: 2rem; }
                h1 { font-size: 1.75rem; margin-bottom: 0.5rem; }
                h2 { font-size: 1.25rem; margin-bottom: 0.75rem; color: var(--text-muted); }
                .timestamp { color: var(--text-muted); font-size: 0.875rem; }
                .summary-stats {
                    margin-top: 1rem;
                    display: flex;
                    gap: 1.5rem;
                    font-size: 1rem;
                    font-weight: 600;
                }
                .stat { padding: 0.25rem 0.75rem; border-radius: 4px; }
                table {
                    width: 100%;
                    border-collapse: collapse;
                    background: var(--bg-white);
                    border: 1px solid var(--border-color);
                    border-radius: 4px;
                    margin-bottom: 1.5rem;
                }
                thead th {
                    background: var(--bg-light);
                    padding: 0.75rem;
                    text-align: left;
                    font-size: 0.875rem;
                    border-bottom: 2px solid var(--border-color);
                }
                tbody td {
                    padding: 0.75rem;
                    border-bottom: 1px solid var(--border-color);
                    font-size: 0.875rem;
                    vertical-align: top;
                }
                tbody tr:last-child td { border-bottom: none; }
                details > summary {
                    cursor: pointer;
                    font-weight: 500;
                }
                details > summary:hover { text-decoration: underline; }
                pre.level2, pre.level3 {
                    margin: 0.5rem 0 0.5rem 1.5rem;
                    padding: 0.75rem;
                    background: var(--bg-light);
                    border: 1px solid var(--border-color);
                    border-left: 3px solid var(--border-color);
                    border-radius: 4px;
                    font-size: 0.8125rem;
                    overflow-x: auto;
                    white-space: pre-wrap;
                    word-wrap: break-word;
                }
                details details {
                    margin-left: 1.5rem;
                }
                pre.level3 {
                    overflow: visible;
                }
                pre.level3 span.tip {
                    border-bottom: 1px dotted var(--text-muted);
                    cursor: help;
                    position: relative;
                }
                pre.level3 span.tip:hover::after {
                    content: attr(data-tip);
                    position: absolute;
                    left: 0;
                    bottom: 1.4em;
                    background: #333;
                    color: #fff;
                    padding: 0.4em 0.6em;
                    border-radius: 4px;
                    font-size: 0.75rem;
                    white-space: normal;
                    max-width: 320px;
                    z-index: 10;
                    pointer-events: none;
                }
                .punit-pass { color: var(--pass-color); font-weight: 600; }
                .punit-fail { color: var(--fail-color); font-weight: 600; }
                .punit-inconclusive { color: var(--inconclusive-color); font-weight: 600; }
                .per-criterion { padding: 0.5rem 0 0.25rem 0.5rem; }
                .criterion-block { margin: 0.25rem 0 0.75rem 0; }
                .criterion-block h4 {
                    font-size: 0.875rem;
                    font-family: monospace;
                    margin-bottom: 0.25rem;
                }
                .criterion-block dl {
                    display: grid;
                    grid-template-columns: max-content 1fr;
                    column-gap: 1rem;
                    row-gap: 0.1rem;
                    font-size: 0.8125rem;
                    margin-left: 0.5rem;
                }
                .criterion-block dt { color: var(--text-muted); }
                .criterion-block dd { font-family: monospace; }
                .latency-observed { color: #adb5bd; }
                .latency-pass { color: var(--pass-color); font-weight: 600; }
                .latency-fail { color: var(--fail-color); font-weight: 600; }
                .test-group { margin-bottom: 2rem; }
                .banner-inconclusive {
                    margin-top: 1rem;
                    padding: 0.75rem 1rem;
                    background: #f3e5f5;
                    border: 1px solid var(--inconclusive-color);
                    border-left: 4px solid var(--inconclusive-color);
                    border-radius: 4px;
                    font-size: 0.875rem;
                    color: var(--text-color);
                }
                .banner-inconclusive code {
                    background: var(--bg-white);
                    padding: 0.1rem 0.4rem;
                    border-radius: 3px;
                    font-size: 0.8125rem;
                }
                .misalignment-guidance {
                    margin: 0.5rem 0 0.5rem 1.5rem;
                    padding: 0.5rem 0.75rem;
                    background: #f3e5f5;
                    border: 1px solid var(--inconclusive-color);
                    border-left: 3px solid var(--inconclusive-color);
                    border-radius: 4px;
                    font-size: 0.8125rem;
                }
                .misalignment-guidance code {
                    background: var(--bg-white);
                    padding: 0.1rem 0.4rem;
                    border-radius: 3px;
                }
                table.postcondition-failures {
                    margin: 0.5rem 0 0.5rem 1.5rem;
                    border-collapse: collapse;
                    font-size: 0.8125rem;
                    background: var(--bg-white);
                }
                table.postcondition-failures th,
                table.postcondition-failures td {
                    padding: 0.4rem 0.6rem;
                    border: 1px solid var(--border-color);
                    vertical-align: top;
                    text-align: left;
                }
                table.postcondition-failures th {
                    background: #f8f9fa;
                    font-weight: 600;
                    color: var(--text-muted);
                }
                table.postcondition-failures td.clause { font-weight: 500; }
                table.postcondition-failures td.count {
                    text-align: right;
                    font-variant-numeric: tabular-nums;
                    color: var(--fail-color);
                    font-weight: 600;
                }
                table.postcondition-failures td.exemplars ul {
                    margin: 0;
                    padding-left: 1rem;
                }
                table.postcondition-failures td.exemplars li { margin: 0.1rem 0; }
                table.postcondition-failures td.exemplars code {
                    background: #f3f4f6;
                    padding: 0.05rem 0.3rem;
                    border-radius: 3px;
                }
                table.postcondition-failures .no-exemplars {
                    color: var(--text-muted);
                    font-style: italic;
                }
                .assumptions {
                    margin-bottom: 1.5rem;
                    background: var(--bg-white);
                    border: 1px solid var(--border-color);
                    border-radius: 4px;
                    font-size: 0.875rem;
                }
                .assumptions > summary {
                    padding: 0.75rem 1rem;
                    cursor: pointer;
                    font-weight: 600;
                    color: var(--text-muted);
                }
                .assumptions > summary:hover { color: var(--text-color); }
                .assumptions-body {
                    padding: 0.75rem 1rem 1rem;
                    border-top: 1px solid var(--border-color);
                    line-height: 1.6;
                }
                .assumptions-body p { margin-bottom: 0.75rem; }
                .assumptions-body ul {
                    margin: 0 0 0.75rem 1.5rem;
                    list-style-type: disc;
                }
                .assumptions-body li { margin-bottom: 0.35rem; }
                .assumptions-warning {
                    padding: 0.5rem 0.75rem;
                    background: #fff8e1;
                    border-left: 3px solid #f9a825;
                    border-radius: 4px;
                }
                footer {
                    margin-top: 2rem;
                    padding-top: 1rem;
                    border-top: 1px solid var(--border-color);
                    color: var(--text-muted);
                    font-size: 0.8125rem;
                }
                """);
        html.append("</style>\n");
    }
}
