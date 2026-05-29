package org.mavai.punit.api.covariate;

import java.time.LocalTime;
import java.util.Objects;

/**
 * A named time-of-day period forming a single partition.
 *
 * <p>Periods use half-open intervals {@code [start, start+durationMinutes)}.
 * The period must not cross midnight: {@code start + durationMinutes <= 24:00}.
 *
 * <p>The partition label is derived automatically from the start time and duration
 * (e.g., {@code "08:00/2h"}, {@code "08:00/30m"}, {@code "08:00/2h30m"}).
 *
 * @param start the start time (inclusive)
 * @param durationMinutes the duration in minutes (positive, no midnight crossing)
 */
public record TimePeriodDefinition(LocalTime start, int durationMinutes) {

    public TimePeriodDefinition {
        Objects.requireNonNull(start, "start must not be null");
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException(
                    "durationMinutes must be positive, got: " + durationMinutes
                            + ". Expected format: 'HH:mm/Nh', 'HH:mm/Nm', or 'HH:mm/NhMm'"
                            + " (e.g., '08:00/2h', '08:00/30m', '08:00/2h30m')");
        }
        int startMinutes = start.getHour() * 60 + start.getMinute();
        if (startMinutes + durationMinutes > 24 * 60) {
            throw new IllegalArgumentException(
                    "Period must not cross midnight: " + start + " + " + durationMinutes
                            + "m exceeds 24:00. Expected format: 'HH:mm/Nh', 'HH:mm/Nm', or 'HH:mm/NhMm'"
                            + " (e.g., '08:00/2h', '08:00/30m', '08:00/2h30m')."
                            + " Reduce the duration or adjust the start time.");
        }
    }

    /**
     * Returns the canonical label for this period (e.g., {@code "08:00/2h"}).
     *
     * @return the formatted label
     */
    public String label() {
        return formatDuration(start, durationMinutes);
    }

    /**
     * Returns the end time (exclusive) of this period.
     *
     * @return the end time
     */
    public LocalTime end() {
        return start.plusMinutes(durationMinutes);
    }

    /**
     * Returns true if the given time falls within this period (half-open interval).
     *
     * @param time the time to check
     * @return true if {@code start <= time < end}
     */
    public boolean contains(LocalTime time) {
        return !time.isBefore(start) && time.isBefore(end());
    }

    /**
     * Formats a time and duration into the canonical period label.
     *
     * @param start the start time
     * @param durationMinutes the duration in minutes
     * @return the formatted label (e.g., "08:00/2h", "08:00/30m", "08:00/2h30m")
     */
    public static String formatDuration(LocalTime start, int durationMinutes) {
        String timeStr = String.format("%02d:%02d", start.getHour(), start.getMinute());
        int hrs = durationMinutes / 60;
        int mins = durationMinutes % 60;
        if (mins == 0) {
            return String.format("%s/%dh", timeStr, hrs);
        }
        if (hrs == 0) {
            return String.format("%s/%dm", timeStr, mins);
        }
        return String.format("%s/%dh%dm", timeStr, hrs, mins);
    }
}
