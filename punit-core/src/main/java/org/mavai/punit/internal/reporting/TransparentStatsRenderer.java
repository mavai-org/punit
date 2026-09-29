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
import org.mavai.punit.api.PercentileKey;
import org.mavai.punit.api.spec.ConfigurationRefusal;
import org.mavai.punit.api.spec.PercentileLatency;
import org.mavai.punit.api.spec.VerdictComposition;
import org.mavai.punit.statistics.DecisionRule;
import org.mavai.punit.statistics.Methodology;

/**
 * Verbose statistical breakdown of a {@link ProbabilisticTestResult}
 * for the audit / compliance service contract where the reasoning behind a
 * verdict — including a passing one — has to be visible (Statistical
 * Companion §7.1, §10.2).
 *
 * <p>Authors opt in via
 * {@code PUnit.testing(...).transparentStats()}; this class is the
 * renderer that produces the actual output.
 *
 * <h2>What gets rendered</h2>
 *
 * <p>The test verdict {@code V_test} at the top, with the functional and
 * latency dimensions beside it, what triggered a FAIL or an INCONCLUSIVE,
 * and the Type-I envelopes by direction; then one analysis block per
 * criterion — hypotheses, the versioned decision rule and its alpha, the
 * observed data, the integer decision artefact, the calibration statement
 * naming its random experiment, and what the design can detect; then one
 * block per enforced latency constraint. A configuration refused before
 * any sample ran renders its configuration errors instead.
 *
 * <p>Output is plain text — readable in IDE test consoles,
 * surefire reports, and CI logs without further processing.
 */
// mavai-ref: JVI-G3NPRSS — do not remove (resolves in mavai-orchestrator)
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
        if (result.refused()) {
            sb.append("STATISTICAL ANALYSIS — configuration refused\n\n");
            sb.append("  ").append(testIdentity).append("\n\n");
            sb.append("  Refused before any sample ran (methodology ")
                    .append(Methodology.VERSION).append(")\n");
            for (ConfigurationRefusal refusal : result.refusals()) {
                sb.append("    ").append(refusal.code()).append(" — ")
                        .append(refusal.reason()).append('\n');
            }
            sb.append('\n');
            sb.append("  Test intent: ").append(result.intent()).append('\n');
            return sb.toString();
        }
        sb.append("STATISTICAL ANALYSIS — test verdict: ")
                .append(result.verdict()).append("\n\n");
        sb.append("  ").append(testIdentity).append("\n\n");
        result.composition().ifPresent(c -> renderComposition(sb, c));

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

    /**
     * The test verdict's header: the two dimensions, what decided a FAIL
     * or an INCONCLUSIVE, and the union-bound envelopes by direction.
     */
    private static void renderComposition(StringBuilder sb, VerdictComposition c) {
        sb.append("  Test verdict (methodology ").append(Methodology.VERSION).append(")\n");
        c.rateVerdict().ifPresent(v -> sb.append(label("Functional:", v.name())));
        c.latencyVerdict().ifPresent(v -> sb.append(label("Latency:", v.name())));
        sb.append(label("Test:", c.testVerdict().name()));
        if (!c.triggering().isEmpty()) {
            sb.append(label("Decided by:", c.triggering().stream()
                    .map(t -> (t.kind() == VerdictComposition.Trigger.Kind.LATENCY
                            ? "latency " : "criterion ") + t.id())
                    .collect(java.util.stream.Collectors.joining(", "))));
        }
        c.falseComplianceEnvelope().ifPresent(e -> sb.append(label("False compliance:",
                String.format(Locale.ROOT, "at most %.4f (sum of alpha, compliance decisions)", e))));
        c.falseDegradationSignalEnvelope().ifPresent(e -> sb.append(label("False degradation:",
                String.format(Locale.ROOT, "at most %.4f (sum of alpha, regression decisions)", e))));
        sb.append('\n');
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
        if (detail.get("decisionsByCriterion") instanceof Map<?, ?> decisions) {
            decisions.forEach((id, decision) -> {
                if (decision instanceof Map<?, ?> d) {
                    renderDecision(sb, String.valueOf(id), d);
                }
            });
        } else if (PercentileLatency.NAME.equals(cr.criterionName())
                && detail.containsKey("successfulSamples")) {
            renderLatency(sb, detail);
        } else {
            renderGeneric(sb, cr, detail);
        }
        sb.append('\n');
    }

    /** One methodology criterion's analysis block (§10.2). */
    private static void renderDecision(StringBuilder sb, String id, Map<?, ?> d) {
        Object rule = d.get("decisionRule");
        Object alpha = d.get("alpha");
        sb.append("    ").append(id).append('\n');
        if (DecisionRule.REGRESSION_FISHER.id().equals(rule)) {
            sb.append(label("H₀ (null):", "baseline and test share one success probability"));
            sb.append(label("H₁ (alternative):", "the test's success probability is lower"));
            sb.append(label("Decision rule:", rule + " v1, alpha " + alpha));
            sb.append(label("Observed:", String.format(Locale.ROOT, "K = %s of n = %s (%s)",
                    d.get("successes"), d.get("total"), formatRate(d.get("observed")))));
            sb.append(label("Baseline:", String.format(Locale.ROOT, "K_b = %s of n_b = %s",
                    d.get("baselineSuccesses"), d.get("baselineSampleCount"))));
            sb.append(label("Cutoff:", String.format(Locale.ROOT,
                    "PASS iff K ≥ c = %s (displayed rate %s)",
                    d.get("cutoff"), formatRate(d.get("displayedRate")))));
            sb.append(label("Calibration:", String.format(Locale.ROOT,
                    "were the baseline and the test both drawn afresh from an unchanged "
                            + "service, the rule would signal degradation with probability "
                            + "at most %s", alpha)));
            if (d.get("sizeAtAssumedCommonRate") instanceof Number size) {
                sb.append(label("Size at p̂_b:", String.format(Locale.ROOT,
                        "%.4f (at the assumed common rate; not a property of the run)",
                        size.doubleValue())));
            }
            if (d.get("designAlternativeRate") instanceof Number rate) {
                sb.append(label("Design power:", String.format(Locale.ROOT, "%.4f at rate %s",
                        ((Number) d.get("designPower")).doubleValue(), rate)));
                sb.append(label("Resolved power:", String.format(Locale.ROOT, "%.4f at rate %s",
                        ((Number) d.get("resolvedTestPower")).doubleValue(), rate)));
            } else if (d.get("minimumDetectableDegradation") instanceof Number mdd) {
                sb.append(label("Detectable drop:", String.format(Locale.ROOT,
                        "%.4f with 80%% power (inverts the design power)", mdd.doubleValue())));
            }
        } else if (DecisionRule.COMPLIANCE_EXACT_BINOMIAL.id().equals(rule)) {
            sb.append(label("H₀ (null):", "p ≤ " + formatRate(d.get("threshold"))
                    + " (the requirement is not met)"));
            sb.append(label("H₁ (alternative):", "p > " + formatRate(d.get("threshold"))));
            sb.append(label("Decision rule:", rule + " v1, alpha " + alpha));
            sb.append(label("Requirement:", formatRate(d.get("threshold"))
                    + " (origin: " + d.get("origin") + ")"));
            sb.append(label("Observed:", String.format(Locale.ROOT, "K = %s of n = %s (%s)",
                    d.get("successes"), d.get("total"), formatRate(d.get("observed")))));
            sb.append(label("Smallest passing:", d.containsKey("kMin")
                    ? "PASS iff K ≥ k_min = " + d.get("kMin")
                    : "no count of this size can pass"));
            sb.append(label("Calibration:", String.format(Locale.ROOT,
                    "were the true rate at or below the requirement, the test would falsely "
                            + "declare compliance with probability at most %s", alpha)));
            sb.append(label("False compliance:", formatRate(d.get("falseCompliance"))));
            sb.append(label("Clopper–Pearson:", formatRate(d.get("clopperPearsonLower"))
                    + " (one-sided lower bound; reported, decides nothing)"));
        } else {
            sb.append(label("Observed:", String.format(Locale.ROOT, "%s of %s (%s)",
                    d.get("successes"), d.get("total"), formatRate(d.get("observed")))));
            sb.append(label("Rule:", d.containsKey("origin")
                    ? "zero failures (origin: " + d.get("origin") + ")" : "none"));
        }
    }

    /** One block per enforced latency constraint. */
    private static void renderLatency(StringBuilder sb, Map<String, Object> d) {
        sb.append(label("Successful:", d.get("successfulSamples") + " latencies"));
        for (PercentileKey key : PercentileKey.values()) {
            String k = key.detailKey();
            if (!(d.get("verdict." + k) instanceof String verdict)) {
                continue;
            }
            sb.append("    ").append(k).append(" → ").append(verdict).append('\n');
            sb.append(label("Decision rule:", d.get("decisionRule." + k) + " v1, alpha " + d.get("alpha")));
            if (d.containsKey("withinThreshold." + k)) {
                sb.append(label("Within ceiling:", d.get("withinThreshold." + k) + " of "
                        + d.get("successfulSamples") + " at or below " + d.get("threshold." + k) + " ms"));
                sb.append(label("Required:", d.containsKey("requiredWithin." + k)
                        ? String.valueOf(d.get("requiredWithin." + k))
                        : "no count of this size can pass"));
                if (d.containsKey("observed." + k)) {
                    sb.append(label("Raw percentile:", d.get("observed." + k)
                            + " ms (advisory; decides nothing)"));
                }
            } else if (Boolean.TRUE.equals(d.get("saturated." + k))) {
                sb.append(label("Threshold:", "none — saturated: no baseline rank achieves alpha"));
            } else if (d.containsKey("threshold." + k)) {
                sb.append(label("Threshold:", d.get("threshold." + k) + " ms (baseline rank "
                        + d.get("threshold." + k + ".rank") + ")"));
                sb.append(label("Observed:", d.get("observed." + k) + " ms"));
            }
            if (Boolean.TRUE.equals(d.get("indicative." + k))) {
                sb.append(label("Indicative:", "below the non-degeneracy minimum: a directional "
                        + "signal only"));
            }
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
