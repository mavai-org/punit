package org.javai.punit.internal.engine.criteria;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import org.javai.punit.api.FactorBundle;
import org.javai.punit.api.PercentileKey;
import org.javai.punit.api.TestIntent;
import org.javai.punit.api.spec.BaselineProvider;
import org.javai.punit.api.spec.Criterion;
import org.javai.punit.api.spec.PassRateStatistics;
import org.javai.punit.api.spec.PercentileLatency;
import org.javai.punit.internal.reporting.InfeasibilityMessageRenderer;
import org.javai.punit.statistics.StatisticalDefaults;
import org.javai.punit.statistics.VerificationFeasibilityEvaluator;
import org.javai.punit.statistics.VerificationFeasibilityEvaluator.FeasibilityResult;

/**
 * Pre-flight feasibility gate for probabilistic tests.
 *
 * <p>Per {@link TestIntent}'s contract:
 *
 * <ul>
 *   <li>{@link TestIntent#VERIFICATION} (default) — if the configured
 *       sample size cannot support a confidence-backed claim against
 *       the resolved baseline rate, the test fails fast as misconfigured
 *       (an {@link IllegalStateException}) <em>before</em> the engine
 *       runs any samples.</li>
 *   <li>{@link TestIntent#SMOKE} — silent. The developer has
 *       explicitly declared SMOKE intent, which means "I know this is
 *       undersized; treat it as a sentinel." The gate produces no
 *       warning and the run proceeds.</li>
 * </ul>
 *
 * <p>The one exception is the <strong>soundness floor</strong>: a
 * configured confidence level below
 * {@link StatisticalDefaults#SOUNDNESS_FLOOR_CONFIDENCE} aborts
 * regardless of intent. A test that cannot make a claim at the floor's
 * confidence level cannot underwrite a verdict, and Smoke-intent does
 * not buy past that.
 *
 * <p>The check applies to {@link PassRate} criteria — both contractual
 * (declared SLA / SLO / POLICY threshold) and empirical (threshold
 * derived from a resolved baseline). A contractual claim is no less
 * in need of statistical underwriting than an empirical one: n=50
 * with a 99.99% target at 95% confidence is infeasible regardless of
 * whether the 99.99% came from a measurement or a contract.
 *
 * <p>{@link PercentileLatency} criteria get a parallel check that
 * compares the planned sample count against the order-statistic floor
 * {@code ⌈1/(1-p)⌉} for each asserted percentile. Below the floor the
 * empirical percentile collapses toward the max sample and the
 * assertion stops saying what the author meant. The check emits a
 * warning (not an abort); SMOKE intent silences it.
 *
 * <p>Empirical criteria require a resolvable baseline at check time.
 * When no baseline file matches, feasibility is silently deferred —
 * the criterion will produce {@code INCONCLUSIVE} at evaluate time,
 * which is the correct outcome for "no baseline available."
 *
 * <p>Statistics-isolation rule: the math lives in
 * {@link VerificationFeasibilityEvaluator}; this gate only
 * orchestrates. Diagnostic prose comes from
 * {@link InfeasibilityMessageRenderer}.
 */
// javai-ref: JVI-RDWGWVV — do not remove (resolves in javai-orchestrator)
public final class Feasibility {

    private Feasibility() { }

    /**
     * Check feasibility of a single {@code PassRate} criterion
     * against a resolved baseline.
     *
     * @return an empty list. Throws {@link IllegalStateException}
     *         when intent is {@link TestIntent#VERIFICATION} and the
     *         criterion is infeasible. SMOKE-intent infeasibility is
     *         silent — see the class javadoc.
     */
    public static List<String> check(
            int samples,
            Criterion<?, ?> criterion,
            String serviceContractId,
            FactorBundle factors,
            TestIntent intent,
            BaselineProvider provider) {
        if (criterion instanceof PercentileLatency<?> latency) {
            return checkLatencySampleSize(samples, latency, intent);
        }
        if (!(criterion instanceof PassRate<?> bernoulli)) {
            return List.of();
        }
        // Soundness floor — applies regardless of intent. A test
        // configured below the framework's confidence floor cannot
        // underwrite a verdict; even a Smoke-intent test does not
        // silently produce results at that confidence. This check
        // runs before the rate resolution below, so an empirical
        // criterion configured below the floor aborts here regardless
        // of whether its baseline is on disk yet.
        if (bernoulli.confidence() < StatisticalDefaults.SOUNDNESS_FLOOR_CONFIDENCE) {
            throw new IllegalStateException(
                    InfeasibilityMessageRenderer.renderSoundnessFloorBreach(
                            serviceContractId,
                            bernoulli.confidence(),
                            StatisticalDefaults.SOUNDNESS_FLOOR_CONFIDENCE));
        }
        double rate;
        if (bernoulli.isEmpirical()) {
            Optional<PassRateStatistics> baseline = provider.baselineFor(
                    serviceContractId, factors, criterion.name(), PassRateStatistics.class);
            if (baseline.isEmpty()) {
                return List.of();
            }
            rate = baseline.get().observedPassRate();
        } else {
            OptionalDouble target = bernoulli.contractualTarget();
            if (target.isEmpty()) {
                return List.of();
            }
            rate = target.getAsDouble();
        }
        if (rate <= 0.0 || rate >= 1.0) {
            // The evaluator requires the target in (0, 1) exclusive. A
            // rate of exactly 0 or 1 is degenerate; the criterion will
            // surface that downstream as a deterministic pass/fail
            // (contractual) or an INCONCLUSIVE verdict (empirical).
            return List.of();
        }
        FeasibilityResult result = VerificationFeasibilityEvaluator.evaluate(
                samples, rate, bernoulli.confidence());
        if (result.feasible()) {
            return List.of();
        }
        if (intent == TestIntent.VERIFICATION) {
            throw new IllegalStateException(
                    InfeasibilityMessageRenderer.render(serviceContractId, result, false));
        }
        // SMOKE intent: the developer has declared "I know this is
        // undersized; treat it as a sentinel." Silent — no warning.
        return List.of();
    }

    /**
     * Warn when the planned sample count is below the order-statistic
     * floor for any asserted percentile. The floor is ⌈1/(1-p)⌉:
     * P95 needs ≥ 20, P99 needs ≥ 100. Below the floor the empirical
     * percentile collapses toward the max sample and a population-level
     * claim about the percentile is unsupported.
     *
     * <p>SMOKE intent is silent — symmetric with the pass-rate
     * feasibility gate; the developer has declared "I know this is
     * undersized."
     */
    private static List<String> checkLatencySampleSize(
            int samples, PercentileLatency<?> latency, TestIntent intent) {
        if (intent == TestIntent.SMOKE) {
            return List.of();
        }
        // Confidence-bound existence gate (companion §12.5.2.1): for a
        // distribution-free upper bound to exist within the sample, we
        // need n_s >= ⌈log(α) / log(p)⌉ where α = 1 - confidence.
        // VERIFICATION aborts when this fails; the run cannot
        // underwrite a verdict at the configured confidence. SMOKE is
        // silenced above.
        double alpha = 1.0 - latency.confidence();
        for (PercentileKey key : latency.assertedPercentiles()) {
            int existenceFloor = (int) Math.ceil(Math.log(alpha) / Math.log(key.value()));
            if (samples < existenceFloor) {
                throw new IllegalStateException(String.format(
                        "Latency criterion '%s' asserts %s at confidence %s, but the run is"
                                + " configured for %d samples; the confidence-bound existence"
                                + " gate (Statistical Companion §12.5.2.1) requires at least"
                                + " %d samples for an upper bound to exist within the sample"
                                + " at this confidence. Either raise the sample count, lower"
                                + " the confidence, drop the asserted percentile, or run under"
                                + " SMOKE intent to silence the gate.",
                        latency.name(), key.name(), latency.confidence(),
                        samples, existenceFloor));
            }
        }
        // Non-degeneracy floor (companion §12.5.2): warning only.
        // Surfaces a less-strict floor (P95 → 20, P99 → 100) that the
        // existence gate above generally subsumes at α=0.05; kept as a
        // diagnostic for runs where the existence gate did not fire
        // (e.g. relaxed confidence).
        List<String> warnings = new ArrayList<>();
        for (PercentileKey key : latency.assertedPercentiles()) {
            int floor = minimumSampleSizeFor(key);
            if (samples < floor) {
                warnings.add(String.format(
                        "Latency criterion '%s' asserts %s but the run is"
                                + " configured for %d samples; %s needs at"
                                + " least %d for a meaningful population-level"
                                + " estimate. The percentile will reflect the"
                                + " max sample rather than a tail probability.",
                        latency.name(), key.name(), samples, key.name(), floor));
            }
        }
        return warnings;
    }

    /**
     * The minimum sample count for which an order-statistic estimate
     * of {@code key} has a meaningful population interpretation —
     * specifically {@code ⌈1 / (1 - p)⌉}.
     *
     * <ul>
     *   <li>P50 → 1 (any non-empty sample)</li>
     *   <li>P90 → 10</li>
     *   <li>P95 → 20</li>
     *   <li>P99 → 100</li>
     * </ul>
     */
    static int minimumSampleSizeFor(PercentileKey key) {
        return switch (key) {
            case P50 -> 1;
            case P90 -> 10;
            case P95 -> 20;
            case P99 -> 100;
        };
    }
}
