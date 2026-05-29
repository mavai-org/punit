package org.mavai.punit.api.covariate;

import java.util.List;
import java.util.Objects;

/**
 * A time-of-day covariate. Each period string takes the form
 * {@code HH:mm/Nh} (start time, slash, duration in hours).
 *
 * <p>Constructor-time validation in this slice covers only the basic
 * shape (non-empty list, non-blank strings). Full parsing — overlap
 * detection, midnight-crossing rejection, hour and minute bounds —
 * happens at resolution time, where the parsed periods are needed.
 *
 * @param periods the period strings; non-empty, each non-blank
 */
public record TimeOfDayCovariate(List<String> periods)
        implements Covariate {

    private static final String NAME = "time_of_day";

    public TimeOfDayCovariate {
        Objects.requireNonNull(periods, "periods");
        if (periods.isEmpty()) {
            throw new IllegalArgumentException(
                    "time-of-day covariate requires at least one period");
        }
        for (int i = 0; i < periods.size(); i++) {
            String period = periods.get(i);
            if (period == null || period.isBlank()) {
                throw new IllegalArgumentException(
                        "time-of-day period at index " + i
                                + " must be non-blank");
            }
        }
        periods = List.copyOf(periods);
    }

    @Override
    public CovariateCategory category() {
        return CovariateCategory.TEMPORAL;
    }

    @Override
    public String name() {
        return NAME;
    }
}
