package org.mavai.punit.internal.engine;

import java.time.Duration;
import java.util.List;

import org.mavai.punit.api.LatencyResult;
import org.mavai.punit.api.ServiceContractOutcome;
import org.mavai.punit.statistics.LatencyStatistics;

/**
 * Bridge between a list of observed {@link ServiceContractOutcome} durations
 * and the {@link LatencyResult} record.
 *
 * <p>Computation delegates to
 * {@link LatencyStatistics#nearestRankPercentile(double[], double)}
 * — the R-compatible nearest-rank percentile, so conformance with
 * the javai-R oracle is preserved.
 *
 * <p>This bridge lives in {@code punit-core} rather than
 * {@code api package} to keep the {@code commons-statistics} dependency
 * out of the author-facing surface.
 */
final class LatencyPercentileComputer {

    private LatencyPercentileComputer() {}

    static <OT> LatencyResult computeFrom(List<ServiceContractOutcome<?, OT>> outcomes) {
        if (outcomes.isEmpty()) {
            return LatencyResult.empty();
        }
        double[] millis = new double[outcomes.size()];
        for (int i = 0; i < outcomes.size(); i++) {
            millis[i] = outcomes.get(i).duration().toNanos() / 1_000_000.0;
        }
        return new LatencyResult(
                toDuration(LatencyStatistics.nearestRankPercentile(millis, 0.50)),
                toDuration(LatencyStatistics.nearestRankPercentile(millis, 0.90)),
                toDuration(LatencyStatistics.nearestRankPercentile(millis, 0.95)),
                toDuration(LatencyStatistics.nearestRankPercentile(millis, 0.99)),
                outcomes.size());
    }

    private static Duration toDuration(double millis) {
        long nanos = Math.round(millis * 1_000_000.0);
        return Duration.ofNanos(Math.max(0L, nanos));
    }
}
