package org.mavai.punit.api.covariate;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.DayOfWeek;

/**
 * Declares a group of days forming a single partition for the day-of-week covariate.
 *
 * <p>Days within a group are considered statistically equivalent. For example,
 * grouping Saturday and Sunday declares that the service contract behaves similarly
 * on both days.
 *
 * <h2>Examples</h2>
 * <pre>{@code
 * // Weekends grouped, weekdays = remainder
 * covariateDayOfWeek = { @DayGroup({SATURDAY, SUNDAY}) }
 *
 * // All days covered, no remainder
 * covariateDayOfWeek = {
 *     @DayGroup({SATURDAY, SUNDAY}),
 *     @DayGroup({MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY})
 * }
 *
 * // Single day (note: braces optional for single element)
 * covariateDayOfWeek = { @DayGroup(MONDAY) }
 * }</pre>
 *
 * @see ServiceContract#covariateDayOfWeek()
 */
@Documented
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface DayGroup {

    /**
     * The days in this group.
     *
     * @return one or more days of the week
     */
    DayOfWeek[] value();

    /**
     * Optional label for this group.
     *
     * <p>If empty, the framework auto-derives a label:
     * <ul>
     *   <li>{@code {SATURDAY, SUNDAY}} &rarr; "WEEKEND"</li>
     *   <li>{@code {MONDAY..FRIDAY}} &rarr; "WEEKDAY"</li>
     *   <li>Otherwise: joined day names (e.g. "MONDAY_TUESDAY")</li>
     * </ul>
     *
     * @return the group label, or empty for auto-derivation
     */
    String label() default "";
}
