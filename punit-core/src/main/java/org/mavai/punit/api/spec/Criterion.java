package org.mavai.punit.api.spec;

import java.util.Map;
import java.util.OptionalDouble;

/**
 * A spec-level claim evaluated against the observed sample aggregate.
 *
 * <p>A criterion is concerned with one statistical model and therefore
 * one kind of baseline statistics. {@code PassRate} reads
 * pass-rate statistics; {@code PercentileLatency} reads latency
 * percentiles. Future criterion kinds add new {@link BaselineStatistics}
 * implementations without touching existing types.
 *
 * <p>Criteria compose on a
 * {@link ProbabilisticTest.Builder} via {@code .criterion(c)} (required,
 * contributes to the combined verdict) or {@code .reportOnly(c)}
 * (evaluated, attached to the result, excluded from composition).
 *
 * @param <OT> the outcome value type the sample loop produced
 * @param <S>  the baseline statistics kind the criterion reads
 */
public interface Criterion<OT, S extends BaselineStatistics> {

    /** Stable identifier used in reports and the composed result. */
    String name();

    /**
     * The statistics kind this criterion reads. The framework uses
     * this to route the right slice of the resolved baseline into the
     * criterion's evaluation context.
     *
     * <p>Criteria that read no baseline return
     * {@code NoStatistics.class}.
     */
    Class<S> statisticsType();

    /**
     * Evaluate this criterion against the observed sample summary and
     * its typed context. Called once per spec run, after the engine
     * has finished sampling. Must be a pure function of the context —
     * no side effects.
     */
    CriterionResult evaluate(EvaluationContext<OT, S> ctx);

    /**
     * @return {@code true} if the criterion's evaluation depends on a
     *         resolved baseline (an empirical claim against measured
     *         data); {@code false} for contractual claims with no
     *         baseline dependency. Default: {@code false}.
     *
     * <p>The framework consults this at build time to gate the
     * inline-sampling form on probabilistic tests: an empirical
     * criterion requires a {@link Sampling} value shared with the
     * paired measure (the structural integrity guarantee for the
     * empirical comparison), so the inline form — which constructs
     * a fresh {@link Sampling} per spec — is rejected with a
     * diagnostic teaching the helper-extraction pattern.
     */
    default boolean isEmpirical() {
        return false;
    }

    /**
     * Snapshot of this criterion's configuration as a flat detail map.
     * Empirical criteria override to surface keys like
     * {@code confidence} (Bernoulli pass-rate) or
     * {@code assertedPercentiles} (percentile latency); contractual
     * criteria return an empty map.
     *
     * <p>Used wherever the framework synthesises an INCONCLUSIVE
     * {@link CriterionResult} on this criterion's behalf (e.g. the
     * preflight short-circuit when no baseline is resolvable) so the
     * result carries the same diagnostic shape the criterion's own
     * post-sampling path would have produced. Each empirical
     * criterion's {@code evaluate} also reads this map to populate
     * the corresponding keys on its own results, keeping one source
     * of truth.
     *
     * @return an ordered map of detail keys — possibly empty — that
     *         the criterion contributes to any diagnostic result
     *         produced on its behalf
     */
    default Map<String, Object> empiricalDetail() {
        return Map.of();
    }

    /**
     * The up-front pass-rate threshold this criterion targets, if any.
     *
     * <p>Returned non-empty only when the criterion is built against a
     * contractual rate known before sampling begins (e.g.
     * {@code PassRate.meeting(0.95, SLA)}). The engine consults this
     * to short-circuit the sample loop when the threshold is
     * mathematically unreachable (failure inevitable) or already
     * guaranteed (success guaranteed). Criteria that derive their
     * threshold at evaluate time — empirical pass-rate modes,
     * percentile-latency claims — leave the default empty and run
     * every planned sample.
     *
     * @return the pass-rate threshold in [0, 1], or empty if this
     *         criterion does not expose an up-front threshold suitable
     *         for early termination
     */
    default OptionalDouble earlyTerminationPassRate() {
        return OptionalDouble.empty();
    }
}
