package org.mavai.punit.statistics;

/**
 * A risk-driven sizing design that cannot be priced, refused with its cause
 * stated as data rather than only as prose.
 *
 * <h2>Why this is an exception and not an {@code Outcome}</h2>
 * <p>An inadmissible sizing design is a <em>misconfiguration</em>: the
 * operator has declared a design the evidence cannot price. Misconfiguration
 * travels on the exception channel; the {@code Outcome} channel carries the
 * anticipated failure of a <em>sample</em>, which this is not. Sizing happens
 * once, at pre-flight, before any sample is taken.
 *
 * <h2>Why it is a type of its own</h2>
 * <p>The refusal was previously an {@link IllegalArgumentException} whose only
 * distinguishing feature was its message. Two causes reach it, and they call
 * for different corrective action:
 * <ul>
 *   <li>{@link Cause#ZERO_BASELINE} — the baseline observed no successes, so
 *       the effective baseline rate is exactly 0 (statistical companion
 *       §4.3.4) and the domain {@code p_min < p₀} is empty for every declared
 *       tolerance. <em>Go and measure a baseline.</em></li>
 *   <li>{@link Cause#EMPTY_TOLERANCE_INTERVAL} — the baseline is usable, but
 *       the declared tolerance does not sit below it, so there is no
 *       degradation to detect. <em>Re-measure the baseline rather than raising
 *       the tolerance.</em></li>
 * </ul>
 *
 * <p>Carrying the cause as an enum lets a caller — a report, a diagnostic, a
 * conformance run — distinguish them without parsing prose.
 */
public class SizingRefusedException extends IllegalArgumentException {

    /** Why a sizing design could not be priced. */
    public enum Cause {
        /** The baseline observed no successes; the sizing domain is empty. */
        ZERO_BASELINE,
        /** The declared tolerance does not sit strictly below the baseline. */
        EMPTY_TOLERANCE_INTERVAL
    }

    private final transient Cause cause;

    public SizingRefusedException(Cause cause, String message) {
        super(message);
        this.cause = cause;
    }

    /** The cause of the refusal, as data. */
    public Cause refusalCause() {
        return cause;
    }
}
