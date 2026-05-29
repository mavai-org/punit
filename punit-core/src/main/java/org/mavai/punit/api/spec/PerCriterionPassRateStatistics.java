package org.mavai.punit.api.spec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-methodology-criterion pass-rate basis. The on-disk shape of
 * the {@code bernoulli-pass-rate} statistics entry from
 * {@code punit-baseline-3} and later: one nested
 * {@link PassRateStatistics} per methodology criterion the MEASURE
 * run recorded, keyed by the criterion's identifier.
 *
 * <p>K=1 contracts produce a single-entry map keyed by the
 * methodology criterion's id (the lone criterion declared by the
 * contract's {@code criteria()}). K&gt;1 contracts produce one entry
 * per methodology criterion declared via {@code Contract.criteria()}.
 *
 * <p>The framework's {@code BaselineProvider} unwraps this structure
 * for callers that request {@link PassRateStatistics} directly: a
 * single-entry map yields its lone entry; a multi-entry map fails
 * fast because the empirical {@code PassRate} evaluator does not yet
 * know which methodology criterion to target. The producer side
 * carries the K&gt;1 data faithfully even though the K&gt;1 consumer
 * path is not yet wired — re-running an empirical TEST against a
 * K&gt;1 baseline surfaces the deliberate gate, not a regression.
 *
 * @param byCriterion ordered map of methodology criterion id to its
 *                    per-criterion pass-rate basis; must be non-null
 */
public record PerCriterionPassRateStatistics(
        Map<String, PassRateStatistics> byCriterion) implements BaselineStatistics {

    public PerCriterionPassRateStatistics {
        Objects.requireNonNull(byCriterion, "byCriterion");
        byCriterion = Collections.unmodifiableMap(new LinkedHashMap<>(byCriterion));
    }

    /**
     * @return the sum of per-criterion sample counts. Note: this is
     *         only meaningful as the "denominator total" for
     *         {@link EmpiricalChecks} when the methodology's
     *         marginal-denominator semantics align with that
     *         summation — for K=1 it equals the single criterion's
     *         sampleCount.
     */
    @Override
    public int sampleCount() {
        int sum = 0;
        for (PassRateStatistics entry : byCriterion.values()) {
            sum += entry.sampleCount();
        }
        return sum;
    }

    /**
     * K=1 convenience factory: wraps a single
     * {@link PassRateStatistics} under the supplied methodology
     * criterion id. The common case for tests and for contracts
     * declaring a single criterion via {@code criteria()}.
     */
    public static PerCriterionPassRateStatistics of(
            String criterionId, PassRateStatistics stats) {
        Objects.requireNonNull(criterionId, "criterionId");
        Objects.requireNonNull(stats, "stats");
        return new PerCriterionPassRateStatistics(Map.of(criterionId, stats));
    }

    /**
     * K=1 convenience factory: builds a single-entry map keyed by
     * the supplied criterion id, with one
     * {@link PassRateStatistics} record built from the supplied
     * observed rate and sample count.
     */
    public static PerCriterionPassRateStatistics of(
            String criterionId, double observedPassRate, int sampleCount) {
        return of(criterionId, new PassRateStatistics(observedPassRate, sampleCount));
    }
}
