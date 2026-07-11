package org.mavai.punit.api.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Derives a per-criterion three-valued verdict for every methodology-
 * level criterion the contract declared on a run.
 *
 * <p>This is glue, not statistics: it reuses the verdicts and
 * thresholds the criterion evaluation already produced (via
 * {@code PassRate.evaluate}). The decision rule itself — integer
 * cutoff on the empirical path, test-side Wilson lower bound on the
 * declared path (statistical companion §3.4 / §3.2) — is applied
 * exactly once, inside the criterion; this helper consumes the
 * criterion's published {@code verdictsByCriterion} rather than
 * re-deriving a verdict of its own. Any new statistical arithmetic
 * belongs in {@code org.mavai.punit.statistics}; this helper performs
 * no such arithmetic.
 *
 * <p>Behavioural contract:
 *
 * <ul>
 *   <li>The legacy spec-layer evaluation (the list of
 *       {@link EvaluatedCriterion}) is the source of truth for the
 *       resolved threshold and for the gate-fired INCONCLUSIVE state.
 *       When the legacy composite is INCONCLUSIVE — e.g. no baseline,
 *       sample-size constraint violated, identity mismatch — every
 *       per-criterion verdict is INCONCLUSIVE too. The framework has
 *       judged the entire run statistically inconclusive; a
 *       per-criterion verdict cannot meaningfully be PASS or FAIL.</li>
 *   <li>Otherwise: per-criterion verdict = the criterion evaluation's
 *       own published verdict for that methodology criterion. For
 *       results from evaluators that publish no per-criterion verdict
 *       map (hand-built fixtures, older evaluators) the legacy
 *       {@code observed >= threshold} comparison remains as the
 *       fallback. A criterion with zero samples (only reachable on a
 *       zero-sample run, in which case the feasibility gate has
 *       independently refused the test) maps to INCONCLUSIVE.</li>
 *   <li>The composite is {@link Verdict#aggregate(List)} over the
 *       per-criterion verdicts.</li>
 * </ul>
 */
// javai-ref: JVI-ZCSHQ5K — do not remove (resolves in javai-orchestrator)
public final class PerCriterionVerdicts {

    private PerCriterionVerdicts() {}

    public static PerCriterionEvaluation derive(
            List<EvaluatedCriterion> legacyEvaluated,
            List<CriterionSampleCounts> perCriterionCounts) {
        if (perCriterionCounts.isEmpty()) {
            return PerCriterionEvaluation.empty();
        }
        java.util.Map<String, Double> perCriterionThresholds =
                scanPerCriterionThresholds(legacyEvaluated);
        java.util.Map<String, Verdict> judgedVerdicts = scanJudgedVerdicts(legacyEvaluated);
        Optional<Double> sharedThreshold = scanThreshold(legacyEvaluated);
        Verdict legacyComposed = Verdict.compose(legacyEvaluated);
        boolean propagateInconclusive = legacyComposed == Verdict.INCONCLUSIVE;

        List<PerCriterionVerdict> derived = new ArrayList<>(perCriterionCounts.size());
        List<Verdict> verdicts = new ArrayList<>(perCriterionCounts.size());
        for (CriterionSampleCounts counts : perCriterionCounts) {
            // Per-criterion threshold takes precedence over the shared
            // single-threshold fallback. The PassRate evaluator publishes
            // a thresholdsByCriterion map in its result detail when K>1;
            // for K=1 it publishes the flat `threshold` field which the
            // shared-threshold fallback picks up.
            double resolvedThreshold = perCriterionThresholds.containsKey(counts.criterionId())
                    ? perCriterionThresholds.get(counts.criterionId())
                    : sharedThreshold.orElse(Double.NaN);
            Verdict v;
            double observed = counts.observedPassRate();
            if (propagateInconclusive || counts.total() == 0) {
                v = Verdict.INCONCLUSIVE;
            } else if (judgedVerdicts.containsKey(counts.criterionId())) {
                // The criterion evaluation already judged this criterion
                // under the companion's decision rule — reuse it; do not
                // re-derive a second verdict from observed vs threshold.
                v = judgedVerdicts.get(counts.criterionId());
            } else if (Double.isNaN(resolvedThreshold)) {
                v = Verdict.INCONCLUSIVE;
            } else {
                v = observed >= resolvedThreshold ? Verdict.PASS : Verdict.FAIL;
            }
            derived.add(new PerCriterionVerdict(
                    counts.criterionId(), v, counts, observed, resolvedThreshold));
            verdicts.add(v);
        }
        return new PerCriterionEvaluation(derived, Verdict.aggregate(verdicts));
    }

    /**
     * Lifts the criterion evaluation's own per-methodology-criterion
     * verdicts from the legacy {@link EvaluatedCriterion} list. The
     * {@code PassRate} evaluator publishes {@code verdictsByCriterion}
     * ({@code Map<String, String>} of verdict names) on its result
     * detail; results that carry no such map (hand-built fixtures,
     * non-pass-rate criteria) contribute nothing and fall back to the
     * threshold comparison.
     */
    private static java.util.Map<String, Verdict> scanJudgedVerdicts(
            List<EvaluatedCriterion> evaluated) {
        java.util.LinkedHashMap<String, Verdict> out = new java.util.LinkedHashMap<>();
        for (EvaluatedCriterion ec : evaluated) {
            Object v = ec.result().detail().get("verdictsByCriterion");
            if (v instanceof java.util.Map<?, ?> raw) {
                for (java.util.Map.Entry<?, ?> e : raw.entrySet()) {
                    if (e.getKey() instanceof String key
                            && e.getValue() instanceof String name) {
                        out.put(key, Verdict.valueOf(name));
                    }
                }
            }
        }
        return out;
    }

    /**
     * Lifts the resolved threshold from the legacy
     * {@link EvaluatedCriterion} list — the single-threshold path
     * used for K=1 runs. The legacy {@code PassRate.evaluate} writes
     * {@code threshold} into its result detail map; the first
     * criterion whose detail carries a numeric threshold wins.
     * Returns empty when no criterion has resolved a threshold yet
     * (all INCONCLUSIVE before derivation, or K&gt;1 where
     * thresholds are per-criterion only).
     */
    private static Optional<Double> scanThreshold(List<EvaluatedCriterion> evaluated) {
        for (EvaluatedCriterion ec : evaluated) {
            Object v = ec.result().detail().get("threshold");
            if (v instanceof Number n) {
                return Optional.of(n.doubleValue());
            }
        }
        return Optional.empty();
    }

    /**
     * Lifts per-methodology-criterion thresholds from the legacy
     * {@link EvaluatedCriterion} list — the K&gt;1 path used since
     * the PassRate evaluator gained per-criterion empirical
     * evaluation. {@code thresholdsByCriterion} on the result detail
     * is a {@code Map<String, Double>} keyed by methodology criterion
     * id. Returns an empty map when no criterion publishes that key
     * (K=1 runs use the shared-threshold path instead).
     */
    private static java.util.Map<String, Double> scanPerCriterionThresholds(
            List<EvaluatedCriterion> evaluated) {
        java.util.LinkedHashMap<String, Double> out = new java.util.LinkedHashMap<>();
        for (EvaluatedCriterion ec : evaluated) {
            Object v = ec.result().detail().get("thresholdsByCriterion");
            if (v instanceof java.util.Map<?, ?> raw) {
                for (java.util.Map.Entry<?, ?> e : raw.entrySet()) {
                    if (e.getKey() instanceof String key
                            && e.getValue() instanceof Number n) {
                        out.put(key, n.doubleValue());
                    }
                }
            }
        }
        return out;
    }
}
