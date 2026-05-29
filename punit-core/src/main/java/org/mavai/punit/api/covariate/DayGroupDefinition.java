package org.mavai.punit.api.covariate;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * A named group of days forming a single partition for the day-of-week covariate.
 *
 * <p>Days within a group are considered statistically equivalent. For example,
 * grouping Saturday and Sunday declares that the service contract behaves similarly
 * on both days.
 *
 * @param days the days in this group (non-empty, immutable)
 * @param label the partition label (non-blank)
 */
public record DayGroupDefinition(Set<DayOfWeek> days, String label) {

    public DayGroupDefinition {
        Objects.requireNonNull(days, "days must not be null");
        Objects.requireNonNull(label, "label must not be null");
        if (days.isEmpty()) {
            throw new IllegalArgumentException("days must not be empty");
        }
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        days = Set.copyOf(EnumSet.copyOf(days));
    }

    /**
     * Returns true if this group contains the given day.
     *
     * @param day the day to check
     * @return true if the day is in this group
     */
    public boolean contains(DayOfWeek day) {
        return days.contains(day);
    }
}
