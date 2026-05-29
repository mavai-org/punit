package org.mavai.punit.api.covariate;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A day-of-week covariate. Days within a single inner set match the
 * same baseline; days falling outside every declared partition form
 * an implicit remainder partition.
 *
 * @param partitions the day-set partitions; non-empty, each set
 *                   non-empty
 */
public record DayOfWeekCovariate(List<Set<DayOfWeek>> partitions)
        implements Covariate {

    private static final String NAME = "day_of_week";

    public DayOfWeekCovariate {
        Objects.requireNonNull(partitions, "partitions");
        if (partitions.isEmpty()) {
            throw new IllegalArgumentException(
                    "day-of-week covariate requires at least one partition");
        }
        for (int i = 0; i < partitions.size(); i++) {
            Set<DayOfWeek> partition = partitions.get(i);
            if (partition == null || partition.isEmpty()) {
                throw new IllegalArgumentException(
                        "day-of-week partition at index " + i
                                + " must be non-empty");
            }
        }
        partitions = List.copyOf(partitions.stream().map(Set::copyOf).toList());
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
