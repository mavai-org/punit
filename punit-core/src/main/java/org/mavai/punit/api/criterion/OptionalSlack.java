package org.mavai.punit.api.criterion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A criterion's optional-check failure budget — how many of its
 * optional postconditions may fail per trial before the trial fails:
 * an absolute count, or an explicit percentage of the trial's
 * applicable optional checks, resolved by <strong>floor</strong> (the
 * conservative reading). The {@code %} suffix is the family's
 * disambiguator — {@code 2} is always a count, {@code "20%"} always a
 * fraction; a bare fraction is refused, never guessed at.
 *
 * <p>Partial credit is a double opt-in: a budget weakens nothing until
 * a postcondition is also marked optional, and an optional mark
 * weakens nothing without a budget (absent budget = zero). This is an
 * acceptance-predicate quantity — counting, not statistics; the
 * criterion's interval, sizing, and verdict machinery consume the
 * per-trial outcome unchanged.
 *
 * @param count the absolute budget, or {@code null} for a percentage
 * @param percent the percentage (of applicable optional checks), or
 *     {@code null} for a count
 * @param display the budget exactly as the author spelled it —
 *     {@code "2"}, {@code "20%"} — carried verbatim into every stated
 *     standings shape
 */
public record OptionalSlack(Integer count, BigDecimal percent, String display) {

    private static final Pattern PERCENT = Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)%$");

    public OptionalSlack {
        if ((count == null) == (percent == null)) {
            throw new IllegalArgumentException(
                    "an optional-slack budget is a count or a percentage, exactly one");
        }
        Objects.requireNonNull(display, "display");
    }

    /** An absolute budget: at most {@code count} optional checks may fail. */
    public static OptionalSlack count(int count) {
        if (count < 0) {
            throw new IllegalArgumentException(
                    "optional-slack takes a non-negative count of optional checks "
                            + "that may fail, got " + count);
        }
        return new OptionalSlack(count, null, String.valueOf(count));
    }

    /**
     * A percentage budget, spelled with the {@code %} suffix
     * ({@code "20%"}), resolved by floor of the trial's applicable
     * optional checks.
     *
     * @throws IllegalArgumentException on any other spelling — a bare
     *     fraction is never guessed at
     */
    public static OptionalSlack percent(String spelling) {
        Objects.requireNonNull(spelling, "spelling");
        Matcher matcher = PERCENT.matcher(spelling.strip());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "optional-slack takes a non-negative whole count, or an explicit "
                            + "percentage like \"20%\" — got \"" + spelling + "\" (a bare "
                            + "fraction is never guessed at)");
        }
        return new OptionalSlack(null, new BigDecimal(matcher.group(1)), spelling.strip());
    }

    /**
     * The budget for a trial with the given number of applicable
     * optional checks: the count as declared, or the percentage
     * resolved by floor. A budget exceeding the applicable count is
     * legal and means "all may fail" — a failure budget cannot be
     * configured into an impossible state.
     */
    public int resolve(int applicableOptional) {
        if (count != null) {
            return count;
        }
        return percent
                .multiply(BigDecimal.valueOf(applicableOptional))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR)
                .intValue();
    }
}
