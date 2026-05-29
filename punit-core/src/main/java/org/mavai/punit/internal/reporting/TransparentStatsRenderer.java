package org.mavai.punit.internal.reporting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.mavai.punit.api.covariate.CovariateAlignment;
import org.mavai.punit.api.covariate.CovariateProfile;
import org.mavai.punit.api.spec.CriterionResult;
import org.mavai.punit.api.spec.CriterionSampleCounts;
import org.mavai.punit.api.spec.EvaluatedCriterion;
import org.mavai.punit.api.spec.FailureCount;
import org.mavai.punit.api.spec.FailureExemplar;
import org.mavai.punit.api.spec.PerCriterionEvaluation;
import org.mavai.punit.api.spec.PerCriterionVerdict;
import org.mavai.punit.api.spec.ProbabilisticTestResult;
import org.mavai.punit.api.spec.Verdict;

/**
 * Verbose statistical breakdown of a {@link ProbabilisticTestResult}
 * for the audit / compliance service contract where the reasoning behind a
 * verdict — including a passing one — has to be visible.
 *
 * <p>Authors opt in via
 * {@code PUnit.testing(...).transparentStats()}; this class is the
 * renderer that produces the actual output.
 *
 * <h2>What gets rendered</h2>
 *
 * <p>For every evaluated criterion, the renderer pulls the
 * structured detail map and formats it into a labelled block:
 *
 * <pre>
 * STATISTICAL ANALYSIS — verdict: PASS
 *
 *   testInstructionTranslation
 *
 *   [REQUIRED] bernoulli-pass-rate → PASS
 *     Hypothesis test
 *       H₀ (null):           True pass rate π ≥ 0.85
 *       H₁ (alternative):    True pass rate π < 0.85
 *       Test type:           One-sided Wilson-score lower bound
 *     Observed data
 *       Sample size (n):     100
 *       Successes (k):       94
 *       Observed rate (p̂):  0.9400
 *     Inference
 *       Wilson 95% lower:    0.8730
 *       Threshold:           0.8500 (origin: SLA)
 *       Reasoning:           0.8730 ≥ 0.8500  ✓
 * </pre>
 *
 * <p>Empirical {@code PassRate} runs get the full
 * hypothesis + Wilson-bound rendering; contractual runs get the
 * simpler {@code observed ≥ threshold} comparison; criteria the
 * renderer doesn't yet know about (latency, future kinds) fall
 * back to the criterion's explanation string plus its raw detail
 * map.
 *
 * <p>Output is plain text — readable in IDE test consoles,
 * surefire reports, and CI logs without further processing.
 */
// javai-ref: JVI-G3NPRSS — do not remove (resolves in javai-orchestrator)
public final class TransparentStatsRenderer {

    private static final String LABEL_INDENT = "      ";
    private static final int LABEL_WIDTH = 22;

    private TransparentStatsRenderer() { }

    /**
     * @param testIdentity the className.methodName the verdict
     *                     belongs to, for the report header
     * @param result       the result to render — the renderer
     *                     reads {@link ProbabilisticTestResult#covariates()
     *                     covariates()} for observed-vs-baseline
     *                     alignment, criterion details, and warnings
     * @return a formatted plain-text report
     */
    public static String render(String testIdentity, ProbabilisticTestResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("STATISTICAL ANALYSIS — verdict: ")
                .append(result.verdict()).append("\n\n");
        sb.append("  ").append(testIdentity).append("\n\n");

        renderCovariates(sb, result.covariates());

        for (EvaluatedCriterion entry : result.criterionResults()) {
            renderCriterion(sb, entry);
        }

        renderPerCriterionEvaluation(sb, result.perCriterionEvaluation());

        renderPostconditionFailures(sb, result.failuresByPostcondition());

        if (!result.warnings().isEmpty()) {
            sb.append("  Notes\n");
            for (String warning : result.warnings()) {
                sb.append("    ! ").append(warning).append("\n");
            }
            sb.append("\n");
        }

        sb.append("  Test intent: ").append(result.intent()).append('\n');
        result.contractRef().ifPresent(ref ->
                sb.append("  Contract: ").append(ref).append('\n'));
        return sb.toString();
    }

    private static void renderCovariates(StringBuilder sb, CovariateAlignment alignment) {
        CovariateProfile observed = alignment.observed();
        CovariateProfile baseline = alignment.baseline();
        if (observed.isEmpty() && baseline.isEmpty()) {
            // Nothing to say — the service contract declared no covariates
            // and no baseline was matched. Skip the section.
            return;
        }
        sb.append("  Covariates\n");
        if (!observed.isEmpty()) {
            sb.append(label("Observed:", formatProfile(observed)));
        }
        if (!baseline.isEmpty()) {
            sb.append(label("Baseline:", formatProfile(baseline)));
        }
        sb.append(label("Aligned:", alignment.aligned() ? "yes" : "no"));
        if (!alignment.mismatches().isEmpty()) {
            for (CovariateAlignment.Mismatch m : alignment.mismatches()) {
                String value = String.format(Locale.ROOT,
                        "observed=%s, baseline=%s",
                        m.observed() == null ? "<absent>" : m.observed(),
                        m.baseline() == null ? "<absent>" : m.baseline());
                sb.append(label("  " + m.covariateKey() + ":", value));
            }
        }
        sb.append('\n');
    }

    private static String formatProfile(CovariateProfile profile) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : profile.values().entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }

    private static void renderCriterion(StringBuilder sb, EvaluatedCriterion entry) {
        CriterionResult cr = entry.result();
        sb.append("  [").append(entry.role()).append("] ")
                .append(cr.criterionName()).append(" → ")
                .append(cr.verdict()).append('\n');

        Map<String, Object> detail = cr.detail();
        switch (cr.criterionName()) {
            case "bernoulli-pass-rate" -> renderBernoulli(sb, cr, detail);
            default -> renderGeneric(sb, cr, detail);
        }
        sb.append('\n');
    }

    private static void renderBernoulli(StringBuilder sb, CriterionResult cr, Map<String, Object> detail) {
        Object origin = detail.get("origin");
        Object threshold = detail.get("threshold");
        Object observed = detail.get("observed");
        Object successes = detail.get("successes");
        Object failures = detail.get("failures");
        Object total = detail.get("total");
        Object confidence = detail.get("confidence");
        Object wilsonLower = detail.get("wilsonLowerBound");
        Object baselineSampleCount = detail.get("baselineSampleCount");

        boolean isEmpirical = "EMPIRICAL".equals(String.valueOf(origin));

        sb.append("    Hypothesis test\n");
        sb.append(label("H₀ (null):",
                "True pass rate π ≥ " + formatRate(threshold)));
        sb.append(label("H₁ (alternative):",
                "True pass rate π < " + formatRate(threshold)));
        sb.append(label("Test type:",
                isEmpirical
                        ? "One-sided Wilson-score lower bound"
                        : "Deterministic comparison (observed ≥ threshold)"));

        sb.append("    Observed data\n");
        sb.append(label("Sample size (n):", String.valueOf(total)));
        sb.append(label("Successes (k):", String.valueOf(successes)));
        sb.append(label("Failures:", String.valueOf(failures)));
        sb.append(label("Observed rate (p̂):", formatRate(observed)));

        sb.append("    Inference\n");
        if (isEmpirical) {
            String confidencePct = confidence == null
                    ? "?"
                    : String.format(Locale.ROOT, "%.0f%%",
                            ((Number) confidence).doubleValue() * 100.0);
            sb.append(label("Wilson " + confidencePct + " lower:", formatRate(wilsonLower)));
            sb.append(label("Threshold:",
                    formatRate(threshold) + " (origin: " + origin + ")"));
            if (baselineSampleCount != null) {
                sb.append(label("Baseline samples:", String.valueOf(baselineSampleCount)));
            }
            sb.append(label("Reasoning:", formatBernoulliReasoning(
                    wilsonLower, threshold, cr.verdict())));
        } else {
            sb.append(label("Threshold:",
                    formatRate(threshold) + " (origin: " + origin + ")"));
            sb.append(label("Reasoning:", formatBernoulliReasoning(
                    observed, threshold, cr.verdict())));
        }
    }

    private static void renderGeneric(StringBuilder sb, CriterionResult cr, Map<String, Object> detail) {
        sb.append("    Explanation: ").append(cr.explanation()).append('\n');
        if (!detail.isEmpty()) {
            sb.append("    Detail\n");
            for (Map.Entry<String, Object> e : detail.entrySet()) {
                sb.append(label(e.getKey() + ":", String.valueOf(e.getValue())));
            }
        }
    }

    private static String label(String text, String value) {
        StringBuilder sb = new StringBuilder(LABEL_INDENT);
        sb.append(text);
        int padding = LABEL_WIDTH - text.length();
        if (padding < 1) padding = 1;
        sb.append(" ".repeat(padding));
        sb.append(value).append('\n');
        return sb.toString();
    }

    private static String formatRate(Object value) {
        if (value == null) return "?";
        if (value instanceof Number n) {
            return String.format(Locale.ROOT, "%.4f", n.doubleValue());
        }
        return String.valueOf(value);
    }

    private static String formatBernoulliReasoning(Object lhs, Object threshold, Verdict verdict) {
        String comparator = verdict == Verdict.PASS ? "≥" : "<";
        String mark = verdict == Verdict.PASS ? " ✓"
                : verdict == Verdict.FAIL ? " ✗"
                : "";
        return formatRate(lhs) + " " + comparator + " " + formatRate(threshold) + mark;
    }

    /**
     * Snapshot the result's evaluated-criteria details as an
     * ordered name → detail map. Useful for tests and tooling that
     * want the structured numbers without re-parsing the rendered
     * text.
     */
    public static Map<String, Map<String, Object>> snapshotCriteria(
            ProbabilisticTestResult result) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        List<EvaluatedCriterion> evaluated = result.criterionResults();
        for (EvaluatedCriterion entry : evaluated) {
            CriterionResult cr = entry.result();
            out.put(cr.criterionName(), cr.detail());
        }
        return out;
    }

    /**
     * Renders the per-postcondition failure histogram. Empty when the
     * contract has no clauses, or when every clause held on every
     * sample. Clauses are presented in descending count order so the
     * most-common failure mode appears first; each clause shows its
     * count and every retained exemplar (engine cap of 3 per clause).
     */
    private static void renderPostconditionFailures(
            StringBuilder sb, Map<String, FailureCount> byClause) {
        if (byClause.isEmpty()) {
            return;
        }
        sb.append("  Postcondition failures\n");
        var ordered = byClause.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().count(), a.getValue().count()))
                .toList();
        for (var entry : ordered) {
            FailureCount bucket = entry.getValue();
            sb.append("    ").append(entry.getKey())
                    .append(" — ").append(bucket.count()).append(" failure")
                    .append(bucket.count() == 1 ? "" : "s").append('\n');
            for (FailureExemplar ex : bucket.exemplars()) {
                sb.append("      • ").append(ex.input())
                        .append(" → ").append(ex.reason()).append('\n');
            }
        }
        sb.append('\n');
    }

    /**
     * Renders the per-criterion methodology-level evaluation: one row
     * per criterion (id, PASS/FAIL/INCONCLUSIVE counts, observed
     * pass-rate, threshold, derived verdict) and the composite verdict
     * — which, since the composite-verdict cutover, drives the
     * contract's overall verdict.
     *
     * <p>Skipped when no per-criterion data was captured (apply-level
     * failure paths, or runs whose engine summary used the
     * back-compat constructor without populating per-criterion counts).
     */
    private static void renderPerCriterionEvaluation(
            StringBuilder sb,
            PerCriterionEvaluation evaluation) {
        List<PerCriterionVerdict> rows = evaluation.perCriterionVerdicts();
        if (rows.isEmpty()) {
            return;
        }
        sb.append("  Per-criterion verdicts\n");
        for (PerCriterionVerdict row : rows) {
            CriterionSampleCounts c = row.counts();
            String observedStr = Double.isNaN(row.observed())
                    ? "n/a"
                    : formatRate(row.observed());
            String thresholdStr = Double.isNaN(row.threshold())
                    ? "n/a"
                    : formatRate(row.threshold());
            sb.append("    ").append(row.criterionId())
                    .append(" → ").append(row.verdict()).append('\n');
            sb.append(label("Counts:", String.format(Locale.ROOT,
                    "pass=%d, fail=%d (condition=%d, transform=%d), total=%d",
                    c.pass(), c.fail(), c.conditionFail(), c.transformFail(),
                    c.total())));
            sb.append(label("Observed rate:", observedStr));
            sb.append(label("Threshold:", thresholdStr));
        }
        sb.append(label("Composite:", evaluation.compositeVerdict().toString()));
        sb.append('\n');
    }
}
