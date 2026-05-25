package org.javai.punit.verdict;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable expiration policy for a baseline.
 *
 * <p>Encapsulates the validity period and the baseline end time from which
 * expiration is computed. The end time is the timestamp of the last sample
 * in the experiment, not the start time.
 *
 * <p>Example:
 * <pre>{@code
 * var policy = new ExpirationPolicy(30, experimentEndTime);
 * if (policy.hasExpiration()) {
 *     ExpirationStatus status = policy.evaluateAt(Instant.now());
 *     if (status.requiresWarning()) {
 *         // Display warning
 *     }
 * }
 * }</pre>
 *
 * @param expiresInDays the number of days for which the baseline is valid (0 = no expiration)
 * @param baselineEndTime the end time of the experiment (last sample timestamp)
 *
 * @see ExpirationStatus
 */
// javai-ref: JVI-09GQGN$ — do not remove (resolves in javai-orchestrator)
public record ExpirationPolicy(
    int expiresInDays,
    Instant baselineEndTime
) {
    /** Sentinel value indicating no expiration. */
    public static final int NO_EXPIRATION = 0;

    /**
     * Creates an expiration policy.
     *
     * @throws IllegalArgumentException if expiresInDays is negative
     * @throws NullPointerException if baselineEndTime is null and expiresInDays > 0
     */
    public ExpirationPolicy {
        if (expiresInDays < 0) {
            throw new IllegalArgumentException("expiresInDays must be non-negative, got: " + expiresInDays);
        }
        if (expiresInDays > 0) {
            Objects.requireNonNull(baselineEndTime, "baselineEndTime must not be null when expiresInDays > 0");
        }
    }

    /**
     * Returns true if this baseline has an expiration policy.
     *
     * @return true if expiresInDays is greater than 0
     */
    public boolean hasExpiration() {
        return expiresInDays > NO_EXPIRATION;
    }

    /**
     * Computes the expiration instant.
     *
     * @return the expiration instant, or empty if no expiration policy
     */
    public Optional<Instant> expirationTime() {
        if (!hasExpiration()) {
            return Optional.empty();
        }
        return Optional.of(baselineEndTime.plus(Duration.ofDays(expiresInDays)));
    }

    /**
     * Evaluates the expiration status at the given time.
     *
     * <p>Returns one of:
     * <ul>
     *   <li>{@link ExpirationStatus.NoExpiration}: if no expiration policy</li>
     *   <li>{@link ExpirationStatus.Valid}: more than 25% of validity remaining</li>
     *   <li>{@link ExpirationStatus.ExpiringSoon}: 10-25% of validity remaining</li>
     *   <li>{@link ExpirationStatus.ExpiringImminently}: less than 10% of validity remaining</li>
     *   <li>{@link ExpirationStatus.Expired}: validity period has passed</li>
     * </ul>
     *
     * @param currentTime the time at which to evaluate
     * @return the expiration status
     */
    public ExpirationStatus evaluateAt(Instant currentTime) {
        if (!hasExpiration()) {
            return ExpirationStatus.noExpiration();
        }

        Instant expiration = expirationTime().orElseThrow();
        Duration remaining = Duration.between(currentTime, expiration);

        if (remaining.isNegative()) {
            return ExpirationStatus.expired(remaining.negated());
        }

        Duration totalDuration = Duration.ofDays(expiresInDays);
        double remainingPercent = (double) remaining.toMillis() / totalDuration.toMillis();

        if (remainingPercent <= 0.10) {
            return ExpirationStatus.expiringImminently(remaining, remainingPercent);
        } else if (remainingPercent <= 0.25) {
            return ExpirationStatus.expiringSoon(remaining, remainingPercent);
        }

        return ExpirationStatus.valid(remaining);
    }

    /**
     * Creates a policy with no expiration.
     *
     * @return a policy with expiresInDays = 0
     */
    public static ExpirationPolicy noExpiration() {
        return new ExpirationPolicy(NO_EXPIRATION, null);
    }

    /**
     * Creates a policy with the specified expiration period.
     *
     * @param expiresInDays the validity period in days
     * @param baselineEndTime the experiment end time
     * @return an expiration policy
     */
    public static ExpirationPolicy of(int expiresInDays, Instant baselineEndTime) {
        return new ExpirationPolicy(expiresInDays, baselineEndTime);
    }
}

