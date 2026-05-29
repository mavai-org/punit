package org.mavai.punit.api.covariate;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;

/**
 * A covariate declaration on a service contract.
 *
 * <p>A covariate is an environmental variable that the developer does
 * not control but that influences outcomes — day of week, time of day,
 * deployment region, timezone, model version, feature flag, and so on.
 * Declaring a covariate tells the framework two things:
 *
 * <ul>
 *   <li><b>What conditions to record.</b> When an experiment runs, the
 *       framework resolves each declared covariate to a concrete value
 *       and stores the resolved profile in the baseline.</li>
 *   <li><b>How to match baselines.</b> When a probabilistic test runs,
 *       the framework resolves the same covariates again and uses the
 *       declared categories to find the matching baseline (or surface
 *       a misalignment when none matches).</li>
 * </ul>
 *
 * <p>Declarations are pure metadata — they describe what <i>can</i>
 * vary, not what <i>is</i> varying right now. Resolution happens at
 * sample time and is the responsibility of a separate component.
 *
 * <p>Five permitted subtypes — four built-ins and one custom:
 *
 * <table>
 *   <caption>Permitted Covariate variants</caption>
 *   <tr><th>Variant</th><th>Category</th><th>Shape</th></tr>
 *   <tr><td>{@link DayOfWeekCovariate}</td><td>TEMPORAL</td>
 *       <td>List of day-set partitions; days outside any partition
 *           form an implicit remainder partition.</td></tr>
 *   <tr><td>{@link TimeOfDayCovariate}</td><td>TEMPORAL</td>
 *       <td>List of period strings in {@code HH:mm/Nh} form.</td></tr>
 *   <tr><td>{@link RegionCovariate}</td><td>INFRASTRUCTURE</td>
 *       <td>List of region-set partitions using ISO 3166-1 alpha-2
 *           codes; unmatched regions form an implicit remainder.</td></tr>
 *   <tr><td>{@link TimezoneCovariate}</td><td>INFRASTRUCTURE</td>
 *       <td>No parameters; system timezone captured as identity.</td></tr>
 *   <tr><td>{@link CustomCovariate}</td><td>caller-specified</td>
 *       <td>A name and a category; resolution mechanism supplied
 *           separately.</td></tr>
 * </table>
 *
 * <p>The hierarchy is sealed so that pattern matching is checked for
 * exhaustiveness at compile time. Adding a sixth variant requires
 * extending the framework — user code cannot.
 */
// javai-ref: JVI-44AD1Q8 — do not remove (resolves in javai-orchestrator)
public sealed interface Covariate
        permits DayOfWeekCovariate,
                TimeOfDayCovariate,
                RegionCovariate,
                TimezoneCovariate,
                CustomCovariate {

    /**
     * The taxonomy category this covariate belongs to. Determines
     * whether mismatches are a hard gate (CONFIGURATION) or soft
     * warning (TEMPORAL, INFRASTRUCTURE, OPERATIONAL,
     * EXTERNAL_DEPENDENCY, DATA_STATE).
     *
     * @return the category; never null
     */
    CovariateCategory category();

    /**
     * The covariate's name. Used as the key in baseline filenames and
     * in covariate-resolution output. Stable, snake_case for
     * built-ins; user-supplied for {@link CustomCovariate}.
     *
     * @return the name; never null or blank
     */
    String name();

    // ── Factory methods ─────────────────────────────────────────────

    /**
     * Declare day-of-week sensitivity with the given partitions.
     *
     * @param partitions one or more day-set partitions; each set must
     *                   be non-empty
     * @throws IllegalArgumentException if {@code partitions} is empty
     *         or any inner set is empty
     */
    static Covariate dayOfWeek(List<Set<DayOfWeek>> partitions) {
        return new DayOfWeekCovariate(partitions);
    }

    /**
     * Declare time-of-day sensitivity with the given period strings.
     *
     * <p>Each period is in the form {@code HH:mm/Nh} (start time,
     * slash, duration). Detailed format validation — overlap
     * detection and midnight-crossing checks — happens at resolution
     * time.
     *
     * @param periods one or more period strings; each must be
     *                non-blank
     * @throws IllegalArgumentException if {@code periods} is empty or
     *         any string is blank
     */
    static Covariate timeOfDay(List<String> periods) {
        return new TimeOfDayCovariate(periods);
    }

    /**
     * Declare region sensitivity with the given partitions.
     *
     * @param partitions one or more region-set partitions; each
     *                   region is a 2-letter ISO 3166-1 alpha-2 code
     * @throws IllegalArgumentException if {@code partitions} is empty,
     *         any inner set is empty, or any region is not a valid
     *         2-letter alpha code
     */
    static Covariate region(List<Set<String>> partitions) {
        return new RegionCovariate(partitions);
    }

    /**
     * Declare timezone sensitivity. The system timezone is captured
     * as an identity covariate (exact-string match, no partitioning).
     */
    static Covariate timezone() {
        return new TimezoneCovariate();
    }

    /**
     * Declare a custom covariate with the given name and category.
     * The resolution mechanism is supplied separately at sample time.
     *
     * @param name     a non-blank, filename-safe key
     * @param category the taxonomy category; non-null
     * @throws IllegalArgumentException if {@code name} is blank
     * @throws NullPointerException if {@code category} is null
     */
    static Covariate custom(String name, CovariateCategory category) {
        return new CustomCovariate(name, category);
    }
}
