package org.mavai.punit.internal.engine.covariate;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses {@code HH:mm/Nh} time-of-day period strings into intervals
 * and matches a given time against them.
 *
 * <p>Supported formats:
 *
 * <ul>
 *   <li>{@code HH:mm/Nh} — N hours (e.g. {@code 08:00/2h})</li>
 *   <li>{@code HH:mm/Nm} — N minutes (e.g. {@code 08:00/30m})</li>
 *   <li>{@code HH:mm/NhMm} — combined (e.g. {@code 08:00/2h30m})</li>
 * </ul>
 *
 * <p>Validation rejects: malformed strings, hour {@code > 23}, minute
 * {@code > 59}, zero-duration periods, periods that cross midnight,
 * and periods that overlap one another.
 *
 * <p>The same period inputs produce the same labels run-to-run so
 * the matcher's output is stable across baselines.
 */
final class PeriodMatcher {

    private static final Pattern PERIOD_PATTERN =
            Pattern.compile("^(\\d{2}):(\\d{2})/(?:(\\d+)h)?(?:(\\d+)m)?$");

    private PeriodMatcher() { }

    /** A parsed period: start instant, duration in minutes, and the
     *  original declaration string used as the period's label. */
    record Parsed(LocalTime start, int durationMinutes, String label) {

        LocalTime end() {
            // No wrap: validation rejects midnight-crossing periods.
            int total = start.getHour() * 60 + start.getMinute() + durationMinutes;
            int hours = total / 60;
            int minutes = total % 60;
            return hours == 24 ? LocalTime.of(0, 0).minusNanos(1) : LocalTime.of(hours, minutes);
        }

        boolean contains(LocalTime t) {
            // [start, start+duration) — half-open
            int target = t.getHour() * 60 + t.getMinute();
            int begin = start.getHour() * 60 + start.getMinute();
            return target >= begin && target < begin + durationMinutes;
        }
    }

    /**
     * Parse and validate a list of period strings. The returned list
     * is in declaration order; overlap and midnight-crossing checks
     * have already passed.
     *
     * @throws IllegalArgumentException if any period is malformed,
     *         out of range, zero-duration, crosses midnight, or
     *         overlaps another period
     */
    static List<Parsed> parse(List<String> periods) {
        List<Parsed> parsed = new ArrayList<>(periods.size());
        for (String period : periods) {
            parsed.add(parseOne(period));
        }

        List<Parsed> sorted = new ArrayList<>(parsed);
        sorted.sort(Comparator.comparing(Parsed::start));
        for (int i = 0; i < sorted.size() - 1; i++) {
            Parsed cur = sorted.get(i);
            Parsed next = sorted.get(i + 1);
            int curEndMin = cur.start().getHour() * 60 + cur.start().getMinute()
                    + cur.durationMinutes();
            int nextStartMin = next.start().getHour() * 60 + next.start().getMinute();
            if (curEndMin > nextStartMin) {
                throw new IllegalArgumentException(
                        "time-of-day periods overlap: '" + cur.label()
                                + "' and '" + next.label() + "'");
            }
        }
        return List.copyOf(parsed);
    }

    /**
     * @return the label of the first parsed period containing
     *         {@code now}, or empty when no period covers it
     */
    static Optional<String> match(LocalTime now, List<Parsed> periods) {
        for (Parsed p : periods) {
            if (p.contains(now)) {
                return Optional.of(p.label());
            }
        }
        return Optional.empty();
    }

    private static Parsed parseOne(String period) {
        Matcher m = PERIOD_PATTERN.matcher(period);
        if (!m.matches() || (m.group(3) == null && m.group(4) == null)) {
            throw new IllegalArgumentException(
                    "Invalid time period format: '" + period
                            + "'. Expected 'HH:mm/Nh', 'HH:mm/Nm', or 'HH:mm/NhMm'");
        }
        int hours = Integer.parseInt(m.group(1));
        int minutes = Integer.parseInt(m.group(2));
        if (hours > 23) {
            throw new IllegalArgumentException(
                    "Invalid hour in time period '" + period
                            + "': " + hours + " (must be 00-23)");
        }
        if (minutes > 59) {
            throw new IllegalArgumentException(
                    "Invalid minute in time period '" + period
                            + "': " + minutes + " (must be 00-59)");
        }
        int durHours = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
        int durMins = m.group(4) != null ? Integer.parseInt(m.group(4)) : 0;
        if (m.group(3) != null && m.group(4) != null && durMins > 59) {
            throw new IllegalArgumentException(
                    "Minutes component must be 0-59 when combined with hours: '"
                            + period + "'");
        }
        int total = durHours * 60 + durMins;
        if (total <= 0) {
            throw new IllegalArgumentException(
                    "Duration must be positive in time period '" + period + "'");
        }
        int startMin = hours * 60 + minutes;
        if (startMin + total > 24 * 60) {
            throw new IllegalArgumentException(
                    "Time period '" + period
                            + "' crosses midnight (start + duration exceeds 24:00)");
        }
        return new Parsed(LocalTime.of(hours, minutes), total, period);
    }
}
