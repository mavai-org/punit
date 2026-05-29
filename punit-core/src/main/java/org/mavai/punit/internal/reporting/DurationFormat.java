package org.mavai.punit.internal.reporting;

import java.time.Duration;

/**
 * Central formatting utility for durations.
 *
 * <p>Two scales serve different audiences:
 * <ul>
 *   <li>{@link #calendar(Duration)} — human-readable calendar scale for baseline
 *       expiration warnings ("3 days", "5 hours", "12 minutes")</li>
 *   <li>{@link #execution(long)} — compact execution scale for pacing and elapsed
 *       time ("1h 30m", "5m 23s", "45s")</li>
 * </ul>
 *
 * @see RateFormat
 */
public final class DurationFormat {

    private DurationFormat() {
        // Utility class — no instantiation
    }

    /**
     * Formats a duration at calendar scale for human readability.
     *
     * <p>Returns the most significant unit only: "3 days", "5 hours",
     * "12 minutes", or "less than a minute".
     *
     * @param duration the duration to format, or {@code null}
     * @return a human-readable duration string
     */
    public static String calendar(Duration duration) {
        if (duration == null) {
            return "unknown";
        }

        long totalDays = duration.toDays();
        if (totalDays > 0) {
            return totalDays + " day" + (totalDays == 1 ? "" : "s");
        }

        long totalHours = duration.toHours();
        if (totalHours > 0) {
            return totalHours + " hour" + (totalHours == 1 ? "" : "s");
        }

        long totalMinutes = duration.toMinutes();
        if (totalMinutes > 0) {
            return totalMinutes + " minute" + (totalMinutes == 1 ? "" : "s");
        }

        return "less than a minute";
    }

    /**
     * Formats a duration at execution scale for compact display.
     *
     * <p>Returns a compact representation: "1h 30m", "5m 23s", "45s",
     * or "&lt;1s" for sub-second durations.
     *
     * @param ms duration in milliseconds
     * @return a compact duration string
     */
    public static String execution(long ms) {
        if (ms < 1000) {
            return "<1s";
        }

        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
}
