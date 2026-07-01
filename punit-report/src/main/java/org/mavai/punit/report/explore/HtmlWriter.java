package org.mavai.punit.report.explore;

import static org.mavai.punit.report.ComparisonReportHtml.UNAVAILABLE;
import static org.mavai.punit.report.ComparisonReportHtml.percent;
import static org.mavai.punit.report.ComparisonReportHtml.round;
import static org.mavai.punit.report.ReportHtml.escape;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import org.mavai.punit.report.ComparisonReportHtml;
import org.mavai.punit.report.ReportHtml;

/**
 * Renders the Explore-experiment comparison report: a single,
 * self-contained HTML page comparing the variants of each service
 * contract against one another, overall and per-criterion.
 *
 * <p>The page reuses the test report's base stylesheet
 * ({@link ReportHtml#appendBaseCss}) — same colour variables, fonts,
 * table styling, and {@code <details>}/{@code <summary>} idiom — and
 * appends a small chart stylesheet for the dependency-free bars and
 * latency strips. There is no JavaScript, no external asset, and no chart
 * library: every chart is inline SVG or a CSS-width bar, so the file
 * renders fully offline.
 */
final class HtmlWriter {

    /** Ranking: best overall pass-rate, then fastest median, then cheapest. */
    private static final Comparator<Variant> BY_RANK =
            Comparator.comparingDouble(Variant::observedRate).reversed()
                    .thenComparingLong(HtmlWriter::p50ForRanking)
                    .thenComparingLong(Variant::avgTimePerSampleMs);

    /**
     * Relative p50 margin below which two equally-reliable adjacent
     * variants are "too close to call". A presentational threshold on the
     * ordering margin — deliberately not a significance test (the
     * comparison report makes no inferential claim that one variant beats
     * another).
     */
    private static final double NEAR_TIE_LATENCY_RELATIVE = 0.05;

    private HtmlWriter() {
    }

    static String generate(List<ServiceComparison> services) {
        Instant now = Instant.now();
        StringBuilder html = new StringBuilder();
        ComparisonReportHtml.appendDocumentHead(html, "PUnit Exploration Comparison");
        ReportHtml.appendBaseCss(html);
        appendChartCss(html);
        html.append("</head>\n<body>\n");

        ComparisonReportHtml.appendHeader(html, "PUnit Exploration Comparison", now);

        html.append("<main>\n");
        if (services.isEmpty()) {
            html.append("<p class=\"empty\">No explorations found. Run an EXPLORE experiment "
                    + "to produce variant data, then regenerate this report.</p>\n");
        } else {
            appendOverview(html, services);
            if (services.stream().anyMatch(s -> hasNearTie(ranked(s)))) {
                appendNearTieLegend(html);
            }
            for (ServiceComparison service : services) {
                appendService(html, service);
            }
        }
        html.append("</main>\n");

        ComparisonReportHtml.appendFooter(html, now);
        html.append("</body>\n</html>\n");
        return html.toString();
    }

    // ── Overview ──────────────────────────────────────────────────────────

    private static void appendOverview(StringBuilder html, List<ServiceComparison> services) {
        html.append("<section class=\"overview\">\n<h2>Overview</h2>\n");
        html.append("<table>\n<thead>\n<tr>");
        html.append("<th>Service</th><th>Variants</th><th>Best overall</th>");
        html.append("</tr>\n</thead>\n<tbody>\n");
        for (ServiceComparison service : services) {
            List<Variant> ranked = ranked(service);
            html.append("<tr>\n");
            html.append("<td>").append(escape(service.service())).append("</td>\n");
            html.append("<td>").append(service.variants().size()).append("</td>\n");
            html.append("<td>").append(bestOverall(ranked)).append("</td>\n");
            html.append("</tr>\n");
        }
        html.append("</tbody>\n</table>\n</section>\n");
    }

    /**
     * The best-overall cell: the top-ranked variant's label, or — when the
     * leading variants are too close to call — the whole leading cluster
     * joined by the near-tie marker.
     */
    private static String bestOverall(List<Variant> ranked) {
        if (ranked.isEmpty()) {
            return "&mdash;";
        }
        StringBuilder cell = new StringBuilder(escape(ranked.get(0).label()));
        for (int i = 1; i < ranked.size() && nearTie(ranked.get(i), ranked.get(i - 1)); i++) {
            cell.append(" <span class=\"tie-mark\">&asymp;</span> ").append(escape(ranked.get(i).label()));
        }
        return cell.toString();
    }

    private static void appendNearTieLegend(StringBuilder html) {
        html.append("<p class=\"tie-legend\"><span class=\"tie-mark\">&asymp;</span> ");
        html.append("Variants sharing a rank are <strong>too close to call</strong>: their pass "
                + "rates are equal and their median latencies differ by less than 5%. This flags a "
                + "narrow ordering margin for the reader's eye &mdash; it is not a significance test, "
                + "and the report makes no claim that one variant is statistically better than another.");
        html.append("</p>\n");
    }

    // ── Per-service section ────────────────────────────────────────────────

    private static void appendService(StringBuilder html, ServiceComparison service) {
        List<Variant> ranked = ranked(service);
        html.append("<section class=\"service\">\n");
        html.append("<h2>").append(escape(service.service())).append("</h2>\n");
        appendLeaderboard(html, ranked);
        appendCriterionMatrix(html, ranked);
        appendLatencyStrips(html, ranked);
        html.append("</section>\n");
    }

    // ── (a) Leaderboard ─────────────────────────────────────────────────────

    private static void appendLeaderboard(StringBuilder html, List<Variant> ranked) {
        long maxLatency = Math.max(1, ranked.stream()
                .flatMapToLong(v -> LongStream.of(v.p50Ms(), v.p95Ms()))
                .filter(ms -> ms != UNAVAILABLE).max().orElse(1));
        long maxAvg = Math.max(1, ranked.stream()
                .mapToLong(Variant::avgTimePerSampleMs).max().orElse(1));

        html.append("<h3>Leaderboard</h3>\n");
        html.append("<table class=\"leaderboard\">\n<thead>\n<tr>");
        html.append("<th>#</th><th>Variant</th><th>Pass rate</th>");
        html.append("<th>p50</th><th>p95</th><th>Avg cost</th>");
        html.append("<th>Samples</th><th>Termination</th>");
        html.append("</tr>\n</thead>\n<tbody>\n");

        int[] ranks = competitionRanks(ranked);
        for (int i = 0; i < ranked.size(); i++) {
            Variant variant = ranked.get(i);
            html.append("<tr>\n");
            appendRankCell(html, ranked, ranks, i);
            appendVariantCell(html, variant);
            appendPassRateCell(html, variant);
            ComparisonReportHtml.appendLatencyCell(html, variant.p50Ms(), maxLatency);
            ComparisonReportHtml.appendLatencyCell(html, variant.p95Ms(), maxLatency);
            ComparisonReportHtml.appendCostCell(html, variant.avgTimePerSampleMs(), variant.totalTokens(), maxAvg);
            html.append("<td class=\"num\">").append(variant.sampleCount()).append("</td>\n");
            ComparisonReportHtml.appendTerminationCell(html, variant.terminationReason());
            html.append("</tr>\n");
        }
        html.append("</tbody>\n</table>\n");
    }

    private static void appendVariantCell(StringBuilder html, Variant variant) {
        html.append("<td>\n<details>\n<summary class=\"")
                .append(variantClass(variant)).append("\">")
                .append(escape(variant.label())).append("</summary>\n");
        html.append("<dl class=\"factor-list\">\n");
        for (Map.Entry<String, Object> factor : variant.factors().entrySet()) {
            html.append("<dt>").append(escape(factor.getKey())).append("</dt>");
            html.append("<dd><pre>").append(escape(String.valueOf(factor.getValue())))
                    .append("</pre></dd>\n");
        }
        html.append("</dl>\n</details>\n</td>\n");
    }

    private static void appendPassRateCell(StringBuilder html, Variant variant) {
        ComparisonReportHtml.appendPassRateCell(html, variant.hasNoSamples(), variant.successes(),
                variant.sampleCount(), variant.observedRate(), variantClass(variant));
    }

    // ── (b) Per-criterion matrix ─────────────────────────────────────────────

    private static void appendCriterionMatrix(StringBuilder html, List<Variant> ranked) {
        ComparisonReportHtml.appendCriterionMatrix(html, "Variant", ranked, Variant::label, Variant::criteria);
    }

    // ── (c) Latency distribution strips ──────────────────────────────────────

    private static void appendLatencyStrips(StringBuilder html, List<Variant> ranked) {
        long maxRaw = Math.max(1, ranked.stream()
                .flatMapToLong(v -> LongStream.of(v.sortedLatenciesMs()))
                .max().orElse(1));
        boolean anyLatency = ranked.stream().anyMatch(v -> v.sortedLatenciesMs().length > 0);
        if (!anyLatency) {
            return;
        }
        html.append("<h3>Latency distribution</h3>\n");
        html.append("<table class=\"latency-strips\">\n<tbody>\n");
        for (Variant variant : ranked) {
            html.append("<tr>\n<td class=\"strip-label\">").append(escape(variant.label())).append("</td>\n");
            html.append("<td>");
            appendLatencyStrip(html, variant, maxRaw);
            html.append("</td>\n</tr>\n");
        }
        html.append("</tbody>\n</table>\n");
    }

    private static void appendLatencyStrip(StringBuilder html, Variant variant, long maxRaw) {
        long[] sorted = variant.sortedLatenciesMs();
        if (sorted.length == 0) {
            html.append("<span class=\"muted\">no passing samples</span>");
            return;
        }
        int width = 360;
        int height = 24;
        html.append("<svg class=\"latency-strip-svg\" viewBox=\"0 0 ").append(width).append(" ").append(height)
                .append("\" width=\"").append(width).append("\" height=\"").append(height)
                .append("\" role=\"img\">");
        double x0 = stripX(sorted[0], maxRaw, width);
        double x1 = stripX(sorted[sorted.length - 1], maxRaw, width);
        html.append("<line x1=\"").append(round(x0)).append("\" y1=\"12\" x2=\"")
                .append(round(x1)).append("\" y2=\"12\" class=\"strip-axis\"/>");
        for (long ms : sorted) {
            html.append("<circle cx=\"").append(round(stripX(ms, maxRaw, width)))
                    .append("\" cy=\"12\" r=\"2\" class=\"strip-dot\"/>");
        }
        if (variant.p50Ms() != UNAVAILABLE) {
            double xp = stripX(variant.p50Ms(), maxRaw, width);
            html.append("<line x1=\"").append(round(xp)).append("\" y1=\"4\" x2=\"")
                    .append(round(xp)).append("\" y2=\"20\" class=\"strip-p50\"/>");
        }
        html.append("</svg>");
        html.append("<span class=\"strip-range\">").append(sorted[0]).append("&ndash;")
                .append(sorted[sorted.length - 1]).append("ms</span>");
    }

    private static double stripX(long valueMs, long maxRaw, int width) {
        int margin = 6;
        return margin + ((double) valueMs / maxRaw) * (width - 2 * margin);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static long p50ForRanking(Variant variant) {
        return variant.p50Ms() == UNAVAILABLE ? Long.MAX_VALUE : variant.p50Ms();
    }

    private static List<Variant> ranked(ServiceComparison service) {
        return service.variants().stream().sorted(BY_RANK).toList();
    }

    /** Whether any adjacent pair in rank order is too close to call. */
    private static boolean hasNearTie(List<Variant> ranked) {
        for (int i = 1; i < ranked.size(); i++) {
            if (nearTie(ranked.get(i), ranked.get(i - 1))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Two variants are too close to call when they are equally reliable
     * (identical observed pass rate) and their medians differ by less than
     * {@link #NEAR_TIE_LATENCY_RELATIVE}. A difference in pass rate is
     * never softened this way — judging whether two proportions are "close"
     * would be a significance test, which this report does not perform.
     * A variant without a median (no passing samples) is never a near-tie.
     */
    private static boolean nearTie(Variant a, Variant b) {
        if (a.observedRate() != b.observedRate()) {
            return false;
        }
        if (a.p50Ms() == UNAVAILABLE || b.p50Ms() == UNAVAILABLE) {
            return false;
        }
        long larger = Math.max(a.p50Ms(), b.p50Ms());
        if (larger == 0) {
            return true;
        }
        double relative = Math.abs(a.p50Ms() - b.p50Ms()) / (double) larger;
        return relative < NEAR_TIE_LATENCY_RELATIVE;
    }

    /**
     * Competition ranking (1, 1, 3, …): adjacent near-tied variants share
     * the rank of the first member of their run, and the next distinct
     * variant resumes at its positional rank.
     */
    private static int[] competitionRanks(List<Variant> ranked) {
        int[] ranks = new int[ranked.size()];
        for (int i = 0; i < ranked.size(); i++) {
            ranks[i] = (i > 0 && nearTie(ranked.get(i), ranked.get(i - 1))) ? ranks[i - 1] : i + 1;
        }
        return ranks;
    }

    private static void appendRankCell(StringBuilder html, List<Variant> ranked, int[] ranks, int i) {
        boolean tiedWithPrev = i > 0 && nearTie(ranked.get(i), ranked.get(i - 1));
        boolean tiedWithNext = i + 1 < ranked.size() && nearTie(ranked.get(i + 1), ranked.get(i));
        html.append("<td class=\"rank\">").append(ranks[i]);
        if (tiedWithPrev || tiedWithNext) {
            html.append("<span class=\"tie-mark\" title=\"Too close to call: equal pass rate and "
                    + "median latency within 5% of the adjacent variant — a presentational margin, "
                    + "not a significance test.\">&asymp;</span>");
        }
        html.append("</td>\n");
    }

    private static String variantClass(Variant variant) {
        if (variant.hasNoSamples()) {
            return "punit-inconclusive";
        }
        return variant.observedRate() >= 1.0 ? "punit-pass" : "punit-fail";
    }

    private static void appendChartCss(StringBuilder html) {
        html.append("<style>\n");
        ComparisonReportHtml.appendSharedChartCss(html);
        html.append("""
                table.latency-strips td { vertical-align: middle; }
                table.latency-strips td.strip-label { font-weight: 500; white-space: nowrap; }
                svg.latency-strip-svg { vertical-align: middle; }
                .strip-axis { stroke: var(--border-color); stroke-width: 1; }
                .strip-dot { fill: #6c757d; }
                .strip-p50 { stroke: var(--pass-color); stroke-width: 2; }
                .strip-range {
                    margin-left: 0.5rem;
                    font-size: 0.8125rem;
                    color: var(--text-muted);
                    font-variant-numeric: tabular-nums;
                }
                """);
        html.append("</style>\n");
    }
}
