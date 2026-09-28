package org.mavai.punit.internal.engine.criteria;

import java.util.List;
import java.util.Optional;

import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.spec.BaselineProvider;
import org.mavai.punit.api.spec.Criterion;
import org.mavai.punit.api.spec.LatencyStatistics;
import org.mavai.punit.api.spec.PercentileLatency;
import org.mavai.punit.internal.reporting.InfeasibilityMessageRenderer;
import org.mavai.punit.statistics.StatisticalDefaults;

/**
 * Pre-flight checks for probabilistic tests that are not configuration
 * refusals.
 *
 * <p>Configuration refusals — a test larger than its baseline, a
 * normative design no outcome could pass under VERIFICATION — are judged
 * by the engine before any sample runs, and a refused test still writes
 * its verdict record (Statistical Companion §5.7.1). What remains here:
 *
 * <ul>
 *   <li>the <strong>soundness floor</strong>: a configured confidence
 *       below {@link StatisticalDefaults#SOUNDNESS_FLOOR_CONFIDENCE}
 *       aborts regardless of intent — a test that cannot make a claim at
 *       the floor's confidence level cannot underwrite a verdict;</li>
 *   <li>the <strong>latency planning checks</strong> of a
 *       baseline-derived constraint under VERIFICATION (§12.5.3):
 *       non-degeneracy and the precedence rank's existence at the
 *       expected number of successful latencies, reported as warnings
 *       with a planning figure. They decide nothing: both are decided on
 *       the actual count after the run.</li>
 * </ul>
 */
// mavai-ref: JVI-RDWGWVV — do not remove (resolves in mavai-orchestrator)
public final class Feasibility {

    private Feasibility() { }

    /**
     * Pre-flight check of one criterion.
     *
     * @return planning warnings to print before the run; empty when there
     *         are none
     * @throws IllegalStateException when the criterion's confidence is
     *         below the soundness floor
     */
    public static List<String> check(
            int samples,
            Criterion<?, ?> criterion,
            String serviceContractId,
            FactorBundle factors,
            TestIntent intent,
            BaselineProvider provider) {
        if (criterion instanceof PercentileLatency<?> latency) {
            if (intent == TestIntent.SMOKE || !latency.isEmpirical()) {
                return List.of();
            }
            Optional<LatencyStatistics> baseline = provider.baselineFor(
                    serviceContractId, factors, latency.name(), LatencyStatistics.class);
            return baseline
                    .map(b -> latency.planningWarnings(samples, b))
                    .orElse(List.of());
        }
        if (criterion instanceof PassRate<?> bernoulli
                && bernoulli.confidence() < StatisticalDefaults.SOUNDNESS_FLOOR_CONFIDENCE) {
            throw new IllegalStateException(
                    InfeasibilityMessageRenderer.renderSoundnessFloorBreach(
                            serviceContractId,
                            bernoulli.confidence(),
                            StatisticalDefaults.SOUNDNESS_FLOOR_CONFIDENCE));
        }
        return List.of();
    }
}
