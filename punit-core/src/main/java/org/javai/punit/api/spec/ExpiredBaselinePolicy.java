package org.javai.punit.api.spec;

import java.util.Locale;

/**
 * What an empirical probabilistic test does when the baseline it
 * resolves has passed its validity window (see baseline expiration).
 *
 * <p>An expired baseline always surfaces as a verdict caveat (a
 * warning on {@link ProbabilisticTestResult#warnings()}). This policy
 * decides whether expiry <em>also</em> fails the verdict.
 *
 * <ul>
 *   <li>{@link #WARN} (default) — the caveat is informational; the
 *       verdict reflects the statistics.</li>
 *   <li>{@link #FAIL} — an expired baseline forces the verdict to
 *       {@link Verdict#FAIL}, regardless of the observed statistics.
 *       A stale baseline means the empirical comparison itself is
 *       untrustworthy, so a statistical PASS against it must not
 *       stand.</li>
 * </ul>
 *
 * <p>The policy is an operational decision (how strict CI should be),
 * not a per-contract property — it is resolved from the environment
 * via {@link #fromEnvironment()} with the framework's standard
 * precedence (system property, then environment variable, then
 * default), the same shape as the suite-budget configuration.
 */
public enum ExpiredBaselinePolicy {

    /** Expiry is a warning only; the verdict reflects the statistics. */
    WARN,

    /** Expiry forces the verdict to FAIL. */
    FAIL;

    /** System property consulted first. */
    public static final String PROPERTY = "punit.expiration.policy";

    /** Environment variable consulted when the system property is unset. */
    public static final String ENV_VAR = "PUNIT_EXPIRATION_POLICY";

    /**
     * Resolve the policy from the environment: system property
     * {@value #PROPERTY} first, then environment variable
     * {@value #ENV_VAR}, then the default {@link #WARN}.
     *
     * @throws IllegalArgumentException if a value is present but is
     *         neither {@code WARN} nor {@code FAIL} (case-insensitive)
     */
    public static ExpiredBaselinePolicy fromEnvironment() {
        String raw = System.getProperty(PROPERTY);
        if (raw == null) {
            raw = System.getenv(ENV_VAR);
        }
        return parse(raw);
    }

    /**
     * Parse a configured value; {@code null} / blank yields the
     * default {@link #WARN}. Unknown values fail fast.
     */
    public static ExpiredBaselinePolicy parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return WARN;
        }
        String normalised = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalised) {
            case "WARN" -> WARN;
            case "FAIL" -> FAIL;
            default -> throw new IllegalArgumentException(
                    "Unknown expiration policy '" + raw + "' (from " + PROPERTY + " / "
                            + ENV_VAR + "); accepted values are WARN and FAIL");
        };
    }
}
