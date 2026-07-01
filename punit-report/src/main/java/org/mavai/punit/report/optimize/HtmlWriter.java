package org.mavai.punit.report.optimize;

import static org.mavai.punit.report.ComparisonReportHtml.UNAVAILABLE;
import static org.mavai.punit.report.ComparisonReportHtml.percent;
import static org.mavai.punit.report.ComparisonReportHtml.round;
import static org.mavai.punit.report.ReportHtml.escape;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.LongStream;

import org.mavai.punit.report.ComparisonReportHtml;
import org.mavai.punit.report.ReportHtml;

/**
 * Renders the OPTIMIZE-experiment comparison report: a single,
 * self-contained HTML page summarising each optimize run — its iterations
 * listed in run order with a score-derived rank column, the chosen winner
 * highlighted, a per-criterion matrix, and the score trajectory across the
 * run.
 *
 * <p>Reuses the test report's base stylesheet
 * ({@link ReportHtml#appendBaseCss}) for visual parity with the test
 * report and the exploration report, then appends a chart stylesheet for
 * the dependency-free bars and inline-SVG strips. No JavaScript, no
 * external asset, no chart library — the file renders fully offline.
 *
 * <p>Iterations are listed chronologically (by iteration index) so the
 * reader follows the run as a sequence; the score rank is carried in a
 * column rather than by row order. Ranking is objective-aware (a higher
 * score wins under {@code MAXIMIZE}, a lower score under {@code MINIMIZE})
 * and is a plain ordered sort, not a statistical claim.
 */
final class HtmlWriter {

    /**
     * Relative score margin below which two adjacent ranked iterations are
     * "too close to call" on the objective. Presentational — a margin on
     * the ordering, not a significance test.
     */
    private static final double NEAR_TIE_SCORE_RELATIVE = 0.05;

    private HtmlWriter() {
    }

    static String generate(List<OptimizationRun> runs) {
        Instant now = Instant.now();
        StringBuilder html = new StringBuilder();
        ComparisonReportHtml.appendDocumentHead(html, "PUnit Optimization Comparison");
        ReportHtml.appendBaseCss(html);
        appendChartCss(html);
        html.append("</head>\n<body>\n");

        ComparisonReportHtml.appendHeader(html, "PUnit Optimization Comparison", now);

        html.append("<main>\n");
        if (runs.isEmpty()) {
            html.append("<p class=\"empty\">No optimizations found. Run an OPTIMIZE experiment "
                    + "to produce iteration data, then regenerate this report.</p>\n");
        } else {
            appendOverview(html, runs);
            if (runs.stream().anyMatch(r -> hasNearTie(r, ranked(r)))) {
                appendNearTieLegend(html);
            }
            for (OptimizationRun run : runs) {
                appendRun(html, run);
            }
        }
        html.append("</main>\n");

        ComparisonReportHtml.appendFooter(html, now);
        html.append("</body>\n</html>\n");
        return html.toString();
    }

    // ── Overview ──────────────────────────────────────────────────────────

    private static void appendOverview(StringBuilder html, List<OptimizationRun> runs) {
        html.append("<section class=\"overview\">\n<h2>Overview</h2>\n");
        html.append("<table>\n<thead>\n<tr>");
        html.append("<th>Service</th><th>Experiment</th><th>Objective</th>");
        html.append("<th>Iterations</th><th>Best</th>");
        html.append("</tr>\n</thead>\n<tbody>\n");
        for (OptimizationRun run : runs) {
            html.append("<tr>\n");
            html.append("<td>").append(escape(run.service())).append("</td>\n");
            html.append("<td>").append(escape(run.experimentId())).append("</td>\n");
            html.append("<td>").append(escape(run.objective())).append("</td>\n");
            html.append("<td>").append(run.iterations().size()).append("</td>\n");
            html.append("<td>").append(bestCell(run)).append("</td>\n");
            html.append("</tr>\n");
        }
        html.append("</tbody>\n</table>\n</section>\n");
    }

    /**
     * The best cell: the top-ranked iteration, or — when the leading
     * iterations are too close to call — the whole leading cluster joined
     * by the near-tie marker.
     */
    private static String bestCell(OptimizationRun run) {
        List<Iteration> ranked = ranked(run);
        if (ranked.isEmpty()) {
            return "&mdash;";
        }
        StringBuilder cell = new StringBuilder(iterationRef(ranked.get(0)));
        for (int i = 1; i < ranked.size() && nearTie(run, ranked.get(i), ranked.get(i - 1)); i++) {
            cell.append(" <span class=\"tie-mark\">&asymp;</span> ").append(iterationRef(ranked.get(i)));
        }
        return cell.toString();
    }

    private static String iterationRef(Iteration it) {
        return "iteration " + it.index() + " (" + formatScore(it.score()) + ")";
    }

    private static void appendNearTieLegend(StringBuilder html) {
        html.append("<p class=\"tie-legend\"><span class=\"tie-mark\">&asymp;</span> ");
        html.append("Iterations sharing a rank are <strong>too close to call</strong>: their "
                + "scores are within 5% of each other on the objective. This flags a narrow "
                + "ordering margin for the reader's eye &mdash; it is not a significance test, and "
                + "the report makes no claim that one iteration is statistically better than "
                + "another. Tied iterations are ordered among themselves by latency, then cost.");
        html.append("</p>\n");
    }

    // ── Per-run section ─────────────────────────────────────────────────────

    private static void appendRun(StringBuilder html, OptimizationRun run) {
        List<Iteration> ranked = ranked(run);
        List<Iteration> chronological = run.iterations().stream()
                .sorted(Comparator.comparingInt(Iteration::index)).toList();
        html.append("<section class=\"service\">\n");
        html.append("<h2>").append(escape(run.service())).append(" &middot; ")
                .append(escape(run.experimentId())).append("</h2>\n");
        appendConvergence(html, run);
        appendIterationTable(html, run, ranked, chronological);
        appendCriterionMatrix(html, chronological);
        appendScoreTrajectory(html, run);
        html.append("</section>\n");
    }

    private static void appendConvergence(StringBuilder html, OptimizationRun run) {
        Convergence c = run.convergence();
        html.append("<p class=\"convergence\">");
        html.append("Objective <strong>").append(escape(run.objective())).append("</strong>");
        html.append(" &middot; ").append(run.iterations().size()).append(" iterations");
        if (c.bestIteration() >= 0) {
            html.append(" &middot; best: <strong>iteration ").append(c.bestIteration())
                    .append("</strong> (score ").append(formatScore(c.bestScore())).append(")");
        }
        if (c.terminationReason() != null) {
            html.append(" &middot; stopped: ").append(escape(c.terminationReason()));
        }
        html.append("</p>\n");
    }

    // ── (a) Iteration table ───────────────────────────────────────────────────

    /**
     * Iterations in run order (chronological by index). The score rank is a
     * column, not the row order — a leaderboard sort scrambles the sequence
     * the reader thinks in, for no gain the rank column does not already give.
     * The chosen winner gets a row highlight; nothing else is colour-flagged
     * here (a low-but-valid score is not an error — abnormal termination is
     * surfaced by its own cell).
     */
    private static void appendIterationTable(StringBuilder html, OptimizationRun run,
            List<Iteration> ranked, List<Iteration> chronological) {
        long maxLatency = Math.max(1, chronological.stream()
                .flatMapToLong(it -> LongStream.of(it.p50Ms(), it.p95Ms()))
                .filter(ms -> ms != UNAVAILABLE).max().orElse(1));
        long maxAvg = Math.max(1, chronological.stream()
                .mapToLong(Iteration::avgTimePerSampleMs).max().orElse(1));
        double scoreMin = chronological.stream().mapToDouble(Iteration::score).min().orElse(0.0);
        double scoreMax = chronological.stream().mapToDouble(Iteration::score).max().orElse(0.0);
        int best = run.convergence().bestIteration();
        Map<Integer, Integer> rankByIndex = rankByIndex(run, ranked);

        html.append("<h3>Iterations</h3>\n");
        html.append("<table class=\"leaderboard\">\n<thead>\n<tr>");
        html.append("<th>Rank</th><th>Iteration</th><th>Score</th><th>Pass rate</th>");
        html.append("<th>p50</th><th>p95</th><th>Avg cost</th>");
        html.append("<th>Samples</th><th>Termination</th>");
        html.append("</tr>\n</thead>\n<tbody>\n");

        for (Iteration it : chronological) {
            boolean isBest = it.index() == best;
            html.append(isBest ? "<tr class=\"best\">\n" : "<tr>\n");
            html.append("<td class=\"rank\">").append(rankByIndex.get(it.index())).append("</td>\n");
            appendIterationCell(html, it, isBest);
            appendScoreCell(html, it, scoreMin, scoreMax, run.maximize());
            appendPassRateCell(html, it);
            ComparisonReportHtml.appendLatencyCell(html, it.p50Ms(), maxLatency);
            ComparisonReportHtml.appendLatencyCell(html, it.p95Ms(), maxLatency);
            ComparisonReportHtml.appendCostCell(html, it.avgTimePerSampleMs(), it.totalTokens(), maxAvg);
            html.append("<td class=\"num\">").append(it.sampleCount()).append("</td>\n");
            ComparisonReportHtml.appendTerminationCell(html, it.terminationReason());
            html.append("</tr>\n");
        }
        html.append("</tbody>\n</table>\n");
    }

    private static void appendIterationCell(StringBuilder html, Iteration it, boolean best) {
        html.append("<td>\n<details>\n<summary>iteration ").append(it.index());
        if (best) {
            html.append(" <span class=\"badge ok\">best</span>");
        }
        html.append("</summary>\n");
        html.append("<dl class=\"factor-list\">\n");
        for (Map.Entry<String, Object> factor : it.factors().entrySet()) {
            html.append("<dt>").append(escape(factor.getKey())).append("</dt>");
            html.append("<dd><pre>").append(escape(String.valueOf(factor.getValue())))
                    .append("</pre></dd>\n");
        }
        html.append("</dl>\n</details>\n</td>\n");
    }

    private static void appendScoreCell(StringBuilder html, Iteration it,
            double scoreMin, double scoreMax, boolean maximize) {
        double fraction = goodnessFraction(it.score(), scoreMin, scoreMax, maximize);
        html.append("<td class=\"score\">");
        html.append("<div class=\"bar-track\"><div class=\"bar-fill pass\" style=\"width:")
                .append(percent(fraction)).append("\"></div></div>");
        html.append("<span>").append(formatScore(it.score())).append("</span>");
        html.append("</td>\n");
    }

    private static void appendPassRateCell(StringBuilder html, Iteration it) {
        ComparisonReportHtml.appendPassRateCell(html, it.hasNoSamples(), it.successes(),
                it.sampleCount(), it.observedRate(), "");
    }

    // ── (b) Per-criterion matrix ─────────────────────────────────────────────

    private static void appendCriterionMatrix(StringBuilder html, List<Iteration> chronological) {
        ComparisonReportHtml.appendCriterionMatrix(html, "Iteration", chronological,
                it -> "iter " + it.index(), Iteration::criteria);
    }

    // ── (c) Score trajectory ──────────────────────────────────────────────────

    private static void appendScoreTrajectory(StringBuilder html, OptimizationRun run) {
        List<Iteration> byIndex = run.iterations().stream()
                .sorted(Comparator.comparingInt(Iteration::index)).toList();
        if (byIndex.isEmpty()) {
            return;
        }
        double min = byIndex.stream().mapToDouble(Iteration::score).min().orElse(0.0);
        double max = byIndex.stream().mapToDouble(Iteration::score).max().orElse(0.0);
        int best = run.convergence().bestIteration();
        int width = 360;
        int height = 60;
        int marginX = 8;
        int marginY = 10;
        int n = byIndex.size();

        html.append("<h3>Score trajectory</h3>\n");
        html.append("<svg class=\"trajectory-svg\" viewBox=\"0 0 ").append(width).append(" ").append(height)
                .append("\" width=\"").append(width).append("\" height=\"").append(height)
                .append("\" role=\"img\">");

        StringBuilder points = new StringBuilder();
        for (int i = 0; i < n; i++) {
            double x = trajectoryX(i, n, width, marginX);
            double y = trajectoryY(byIndex.get(i).score(), min, max, height, marginY);
            if (i > 0) {
                points.append(' ');
            }
            points.append(round(x)).append(',').append(round(y));
        }
        html.append("<polyline points=\"").append(points).append("\" class=\"trajectory-line\"/>");
        for (int i = 0; i < n; i++) {
            Iteration it = byIndex.get(i);
            double x = trajectoryX(i, n, width, marginX);
            double y = trajectoryY(it.score(), min, max, height, marginY);
            String cls = it.index() == best ? "trajectory-best" : "trajectory-dot";
            html.append("<circle cx=\"").append(round(x)).append("\" cy=\"").append(round(y))
                    .append("\" r=\"").append(it.index() == best ? "3.5" : "2.5")
                    .append("\" class=\"").append(cls).append("\"/>");
        }
        html.append("</svg>");
        html.append("<p class=\"trajectory-note\">Score per iteration (left&rarr;right = iteration order); "
                + "the marked point is the chosen best.</p>\n");
    }

    private static double trajectoryX(int i, int n, int width, int margin) {
        if (n <= 1) {
            return width / 2.0;
        }
        return margin + ((double) i / (n - 1)) * (width - 2 * margin);
    }

    private static double trajectoryY(double score, double min, double max, int height, int margin) {
        double frac = max == min ? 0.5 : (score - min) / (max - min);
        return height - margin - frac * (height - 2 * margin);
    }

    // ── Ranking + near-tie ──────────────────────────────────────────────────────

    private static List<Iteration> ranked(OptimizationRun run) {
        Comparator<Iteration> byScore = Comparator.comparingDouble(Iteration::score);
        if (run.maximize()) {
            byScore = byScore.reversed();
        }
        Comparator<Iteration> rank = byScore
                .thenComparingLong(HtmlWriter::p50ForRanking)
                .thenComparingLong(Iteration::avgTimePerSampleMs);
        return run.iterations().stream().sorted(rank).toList();
    }

    private static boolean hasNearTie(OptimizationRun run, List<Iteration> ranked) {
        for (int i = 1; i < ranked.size(); i++) {
            if (nearTie(run, ranked.get(i), ranked.get(i - 1))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Two iterations are too close to call when their scores are within
     * {@link #NEAR_TIE_SCORE_RELATIVE} of each other. Presentational — the
     * report makes no inferential claim; tied iterations are simply ordered
     * by the secondary keys.
     */
    private static boolean nearTie(OptimizationRun run, Iteration a, Iteration b) {
        double larger = Math.max(Math.abs(a.score()), Math.abs(b.score()));
        if (larger == 0.0) {
            return true;
        }
        return Math.abs(a.score() - b.score()) / larger < NEAR_TIE_SCORE_RELATIVE;
    }

    private static int[] competitionRanks(OptimizationRun run, List<Iteration> ranked) {
        int[] ranks = new int[ranked.size()];
        for (int i = 0; i < ranked.size(); i++) {
            ranks[i] = (i > 0 && nearTie(run, ranked.get(i), ranked.get(i - 1))) ? ranks[i - 1] : i + 1;
        }
        return ranks;
    }

    /**
     * Maps each iteration index to its competition rank. Computed from the
     * ranked (score-sorted) order, then keyed by index so the chronological
     * iteration table can show the rank in a column without reordering its
     * rows. A repeated rank value is how a near-tie reads here — the table
     * carries no separate tie-mark; the too-close-to-call marker lives in the
     * Overview's best cell.
     */
    private static Map<Integer, Integer> rankByIndex(OptimizationRun run, List<Iteration> ranked) {
        int[] ranks = competitionRanks(run, ranked);
        Map<Integer, Integer> byIndex = new LinkedHashMap<>();
        for (int i = 0; i < ranked.size(); i++) {
            byIndex.put(ranked.get(i).index(), ranks[i]);
        }
        return byIndex;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static long p50ForRanking(Iteration it) {
        return it.p50Ms() == UNAVAILABLE ? Long.MAX_VALUE : it.p50Ms();
    }

    private static double goodnessFraction(double score, double min, double max, boolean maximize) {
        if (max == min) {
            return 1.0;
        }
        double frac = maximize ? (score - min) / (max - min) : (max - score) / (max - min);
        return Math.max(0.0, Math.min(1.0, frac));
    }

    private static String formatScore(double score) {
        return String.format(Locale.ROOT, "%.3f", score);
    }

    private static void appendChartCss(StringBuilder html) {
        html.append("<style>\n");
        ComparisonReportHtml.appendSharedChartCss(html);
        html.append("""
                p.convergence { font-size: 0.875rem; color: var(--text-color); margin-bottom: 0.5rem; }
                table.leaderboard tr.best td { background: #f1f8f2; }
                table.leaderboard tr.best td:first-child {
                    box-shadow: inset 3px 0 0 var(--pass-color);
                }
                svg.trajectory-svg {
                    border: 1px solid var(--border-color);
                    border-radius: 4px;
                    background: var(--bg-white);
                    vertical-align: middle;
                }
                .trajectory-line { fill: none; stroke: #adb5bd; stroke-width: 1.5; }
                .trajectory-dot { fill: #6c757d; }
                .trajectory-best { fill: var(--pass-color); }
                p.trajectory-note { font-size: 0.8125rem; color: var(--text-muted); margin-top: 0.25rem; }
                """);
        html.append("</style>\n");
    }
}
