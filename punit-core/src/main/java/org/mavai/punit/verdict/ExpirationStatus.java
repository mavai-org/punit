package org.mavai.punit.verdict;

import java.time.Duration;

/**
 * The expiration status of a baseline at a point in time.
 *
 * <p>This sealed interface represents the possible states of baseline validity:
 * <ul>
 *   <li>{@link NoExpiration}: No expiration policy defined</li>
 *   <li>{@link Valid}: Baseline is valid with ample time remaining</li>
 *   <li>{@link ExpiringSoon}: Baseline expires soon (≤25% validity remaining)</li>
 *   <li>{@link ExpiringImminently}: Baseline expiring imminently (≤10% remaining)</li>
 *   <li>{@link Expired}: Baseline has expired</li>
 * </ul>
 *
 * @see ExpirationPolicy
 */
public sealed interface ExpirationStatus {

    /**
     * No expiration policy defined.
     */
    record NoExpiration() implements ExpirationStatus {
        @Override
        public boolean requiresWarning() {
            return false;
        }

        @Override
        public boolean isExpired() {
            return false;
        }
    }

    /**
     * Baseline is valid with time remaining (more than 25% of validity period).
     *
     * @param remaining the duration until expiration
     */
    record Valid(Duration remaining) implements ExpirationStatus {
        @Override
        public boolean requiresWarning() {
            return false;
        }

        @Override
        public boolean isExpired() {
            return false;
        }
    }

    /**
     * Baseline expires soon (≤25% validity remaining).
     *
     * @param remaining the duration until expiration
     * @param remainingPercent the percentage of validity period remaining (0.0 to 0.25)
     */
    record ExpiringSoon(
        Duration remaining,
        double remainingPercent
    ) implements ExpirationStatus {
        @Override
        public boolean requiresWarning() {
            return true;
        }

        @Override
        public boolean isExpired() {
            return false;
        }
    }

    /**
     * Baseline expiring imminently (≤10% validity remaining).
     *
     * @param remaining the duration until expiration
     * @param remainingPercent the percentage of validity period remaining (0.0 to 0.10)
     */
    record ExpiringImminently(
        Duration remaining,
        double remainingPercent
    ) implements ExpirationStatus {
        @Override
        public boolean requiresWarning() {
            return true;
        }

        @Override
        public boolean isExpired() {
            return false;
        }
    }

    /**
     * Baseline has expired.
     *
     * @param expiredAgo the duration since expiration
     */
    record Expired(Duration expiredAgo) implements ExpirationStatus {
        @Override
        public boolean requiresWarning() {
            return true;
        }

        @Override
        public boolean isExpired() {
            return true;
        }
    }

    // Factory methods

    /**
     * Creates a NoExpiration status.
     *
     * @return a NoExpiration instance
     */
    static ExpirationStatus noExpiration() {
        return new NoExpiration();
    }

    /**
     * Creates a Valid status.
     *
     * @param remaining the duration until expiration
     * @return a Valid instance
     */
    static ExpirationStatus valid(Duration remaining) {
        return new Valid(remaining);
    }

    /**
     * Creates an ExpiringSoon status.
     *
     * @param remaining the duration until expiration
     * @param percent the percentage of validity period remaining
     * @return an ExpiringSoon instance
     */
    static ExpirationStatus expiringSoon(Duration remaining, double percent) {
        return new ExpiringSoon(remaining, percent);
    }

    /**
     * Creates an ExpiringImminently status.
     *
     * @param remaining the duration until expiration
     * @param percent the percentage of validity period remaining
     * @return an ExpiringImminently instance
     */
    static ExpirationStatus expiringImminently(Duration remaining, double percent) {
        return new ExpiringImminently(remaining, percent);
    }

    /**
     * Creates an Expired status.
     *
     * @param expiredAgo the duration since expiration
     * @return an Expired instance
     */
    static ExpirationStatus expired(Duration expiredAgo) {
        return new Expired(expiredAgo);
    }

    /**
     * Returns true if this status requires a warning to be displayed.
     *
     * @return true for ExpiringSoon, ExpiringImminently, and Expired; false otherwise
     */
    boolean requiresWarning();

    /**
     * Returns true if the baseline has expired.
     *
     * @return true only for Expired status
     */
    boolean isExpired();
}

