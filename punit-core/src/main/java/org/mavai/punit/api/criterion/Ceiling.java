package org.mavai.punit.api.criterion;

import java.time.Duration;
import java.util.Objects;

import org.mavai.punit.api.PercentileKey;

/**
 * A per-percentile latency ceiling — the contractual pair
 * {@code (percentile, duration)} consumed internally by
 * {@link LatencyCriterion#meeting}. Authors do not construct
 * {@code Ceiling} directly; they chain
 * {@link LatencyCriterion#atMost(PercentileKey, Duration)} on a
 * {@link Criteria#meeting()} chain.
 *
 * @param percentile the percentile this ceiling applies to
 * @param duration   the per-percentile duration ceiling — strictly
 *                   positive
 */
record Ceiling(PercentileKey percentile, Duration duration) {

    public Ceiling {
        Objects.requireNonNull(percentile, "percentile");
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(
                    "ceiling duration must be > 0, got " + duration);
        }
    }
}
