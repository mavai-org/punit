package org.mavai.punit.api.spec;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Which of a run's assertions bind: every assertion is enforced unless
 * the run makes its dimension advisory (Statistical Companion §12.6).
 *
 * <p>Every functional criterion and every latency constraint — explicit
 * or baseline-derived — is enforced by default. The run-time setting
 * {@value #PROPERTY} (system property, with the {@value #ENV_VAR}
 * environment variable as fallback) names the dimensions that are
 * advisory instead: {@code functional}, {@code latency}, or both as the
 * comma-separated {@code functional,latency}. An advisory dimension is
 * still decided by its rules and reported with its verdict, but it never
 * fails the test. Configuration refusals apply whatever the setting.
 *
 * <p>The setting is an operator's choice for the run — typically
 * {@code latency} on a development machine much slower than the
 * environment a latency requirement was written for — never a property
 * of a contract or a test, so there is no annotation or builder method
 * for it.
 *
 * @param advisory the dimensions the run makes advisory; empty when
 *                 every assertion is enforced
 */
public record AssertionEnforcement(Set<Dimension> advisory) {

    /** System property consulted first. */
    public static final String PROPERTY = "punit.advisory";

    /** Environment variable consulted when the system property is unset. */
    public static final String ENV_VAR = "PUNIT_ADVISORY";

    /** Every assertion enforced: the default. */
    public static final AssertionEnforcement ALL_ENFORCED = new AssertionEnforcement(Set.of());

    /** A dimension of a probabilistic test's verdict. */
    public enum Dimension {

        /** The functional criteria, composed into {@code V_rate}. */
        FUNCTIONAL("functional"),

        /** The latency constraints, composed into {@code V_latency}. */
        LATENCY("latency");

        private final String token;

        Dimension(String token) {
            this.token = token;
        }

        /** The value that names this dimension in {@value AssertionEnforcement#PROPERTY}. */
        public String token() {
            return token;
        }
    }

    public AssertionEnforcement {
        Objects.requireNonNull(advisory, "advisory");
        advisory = advisory.isEmpty()
                ? Set.of()
                : java.util.Collections.unmodifiableSet(EnumSet.copyOf(advisory));
    }

    /** The given dimensions advisory, every other enforced. */
    public static AssertionEnforcement advisory(Dimension... dimensions) {
        return new AssertionEnforcement(Set.of(dimensions));
    }

    /** The mode the run gives a dimension. */
    public EnforcementMode mode(Dimension dimension) {
        Objects.requireNonNull(dimension, "dimension");
        return advisory.contains(dimension) ? EnforcementMode.ADVISORY : EnforcementMode.ENFORCED;
    }

    /**
     * Resolves the run's setting: system property {@value #PROPERTY}
     * first, then environment variable {@value #ENV_VAR}; unset, every
     * assertion is enforced.
     *
     * @throws IllegalArgumentException if the value names anything but
     *         {@code functional} and {@code latency}
     */
    public static AssertionEnforcement fromEnvironment() {
        String raw = System.getProperty(PROPERTY);
        if (raw == null) {
            raw = System.getenv(ENV_VAR);
        }
        return parse(raw);
    }

    /**
     * Parses a configured value: a comma-separated list of
     * {@code functional} and {@code latency}, case-insensitive, blanks
     * around each item ignored. {@code null} or blank leaves every
     * assertion enforced. An unknown or empty item is a configuration
     * error, never ignored.
     *
     * @throws IllegalArgumentException on an unknown or empty item
     */
    public static AssertionEnforcement parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ALL_ENFORCED;
        }
        EnumSet<Dimension> dimensions = EnumSet.noneOf(Dimension.class);
        for (String item : raw.split(",", -1)) {
            dimensions.add(dimensionOf(item.trim().toLowerCase(Locale.ROOT), raw));
        }
        return new AssertionEnforcement(dimensions);
    }

    private static Dimension dimensionOf(String item, String raw) {
        for (Dimension dimension : Dimension.values()) {
            if (dimension.token().equals(item)) {
                return dimension;
            }
        }
        throw new IllegalArgumentException(
                "Unknown advisory setting '" + raw + "' (from " + PROPERTY + " / " + ENV_VAR
                        + "); accepted values are functional, latency, or both as functional,latency");
    }
}
