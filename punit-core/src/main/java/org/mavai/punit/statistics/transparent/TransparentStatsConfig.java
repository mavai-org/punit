package org.mavai.punit.statistics.transparent;

import java.nio.charset.Charset;

/**
 * Configuration for transparent statistics mode.
 *
 * <p>Transparent mode produces verbose statistical explanations of test verdicts,
 * designed for:
 * <ul>
 *   <li><strong>Auditors</strong>: Documented proof that testing methodology is statistically sound</li>
 *   <li><strong>Skeptical stakeholders</strong>: Evidence that AI system reliability claims are justified</li>
 *   <li><strong>New team members</strong>: Understanding of how PUnit reaches its verdicts</li>
 *   <li><strong>Regulators</strong>: Compliance documentation for AI system validation</li>
 * </ul>
 *
 * <h2>Configuration Hierarchy (highest to lowest precedence)</h2>
 * <ol>
 *   <li>{@code @ProbabilisticTest(transparentStats = true)} — per-test override</li>
 *   <li>{@code -Dpunit.stats.transparent=true} — system property</li>
 *   <li>{@code PUNIT_STATS_TRANSPARENT=true} — environment variable</li>
 *   <li>Default: {@code false}</li>
 * </ol>
 *
 * @param enabled Whether transparent mode is enabled
 * @param detailLevel Level of detail in explanations
 * @param format Output format
 */
public record TransparentStatsConfig(
        boolean enabled,
        DetailLevel detailLevel,
        OutputFormat format
) {
    /**
     * System property name for enabling transparent stats.
     */
    public static final String PROP_TRANSPARENT = "punit.stats.transparent";

    /**
     * System property name for detail level.
     */
    public static final String PROP_DETAIL_LEVEL = "punit.stats.detailLevel";

    /**
     * System property name for output format.
     */
    public static final String PROP_FORMAT = "punit.stats.format";

    /**
     * Environment variable name for enabling transparent stats.
     */
    public static final String ENV_TRANSPARENT = "PUNIT_STATS_TRANSPARENT";

    /**
     * Environment variable name for detail level.
     */
    public static final String ENV_DETAIL_LEVEL = "PUNIT_STATS_DETAIL_LEVEL";

    /**
     * Environment variable name for output format.
     */
    public static final String ENV_FORMAT = "PUNIT_STATS_FORMAT";

    /**
     * Level of detail in statistical explanations.
     */
    public enum DetailLevel {
        /**
         * Verdict and key numbers only — skips hypothesis test
         * and statistical inference (standard error, confidence interval,
         * z-test, p-value) sections.
         */
        SUMMARY,

        /**
         * Full explanation including hypothesis test and statistical
         * inference sections (default when enabled).
         */
        VERBOSE
    }

    /**
     * Output format for statistical explanations.
     */
    public enum OutputFormat {
        /**
         * Human-readable with box drawing characters.
         */
        CONSOLE,

        /**
         * For embedding in reports.
         */
        MARKDOWN,

        /**
         * Machine-readable for tooling integration.
         */
        JSON
    }

    /**
     * Resolves the effective configuration from all sources.
     *
     * <p>Priority: annotation override > system property > environment variable > default.
     *
     * @return the resolved configuration
     */
    public static TransparentStatsConfig resolve() {
        return resolve(null);
    }

    /**
     * Resolves the effective configuration with an optional annotation override.
     *
     * @param annotationOverride optional per-test annotation value (null means not set, 
     *                           use system/env defaults)
     * @return the resolved configuration
     */
    public static TransparentStatsConfig resolve(Boolean annotationOverride) {
        boolean enabled = resolveEnabled(annotationOverride);
        DetailLevel detailLevel = resolveDetailLevel();
        OutputFormat format = resolveFormat();

        return new TransparentStatsConfig(enabled, detailLevel, format);
    }

    /**
     * Resolves whether transparent stats are enabled.
     */
    private static boolean resolveEnabled(Boolean annotationOverride) {
        // 1. Annotation override (highest priority)
        if (annotationOverride != null) {
            return annotationOverride;
        }

        // 2. System property
        String sysProp = System.getProperty(PROP_TRANSPARENT);
        if (sysProp != null && !sysProp.isEmpty()) {
            return Boolean.parseBoolean(sysProp);
        }

        // 3. Environment variable
        String envVar = System.getenv(ENV_TRANSPARENT);
        if (envVar != null && !envVar.isEmpty()) {
            return Boolean.parseBoolean(envVar);
        }

        // 4. Default: disabled
        return false;
    }

    /**
     * Resolves the detail level.
     */
    private static DetailLevel resolveDetailLevel() {
        // 1. System property
        String sysProp = System.getProperty(PROP_DETAIL_LEVEL);
        if (sysProp != null && !sysProp.isEmpty()) {
            try {
                return DetailLevel.valueOf(sysProp.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Fall through to default
            }
        }

        // 2. Environment variable
        String envVar = System.getenv(ENV_DETAIL_LEVEL);
        if (envVar != null && !envVar.isEmpty()) {
            try {
                return DetailLevel.valueOf(envVar.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Fall through to default
            }
        }

        // 3. Default
        return DetailLevel.VERBOSE;
    }

    /**
     * Resolves the output format.
     */
    private static OutputFormat resolveFormat() {
        // 1. System property
        String sysProp = System.getProperty(PROP_FORMAT);
        if (sysProp != null && !sysProp.isEmpty()) {
            try {
                return OutputFormat.valueOf(sysProp.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Fall through to default
            }
        }

        // 2. Environment variable
        String envVar = System.getenv(ENV_FORMAT);
        if (envVar != null && !envVar.isEmpty()) {
            try {
                return OutputFormat.valueOf(envVar.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Fall through to default
            }
        }

        // 3. Default
        return OutputFormat.CONSOLE;
    }

    /**
     * Detects whether the terminal supports Unicode.
     *
     * @return true if Unicode is likely supported
     */
    public static boolean supportsUnicode() {
        // Check if we have a console and if the charset appears to be UTF-8
        if (System.console() == null) {
            // Running in IDE or piped output - assume Unicode support
            return true;
        }
        String charsetName = Charset.defaultCharset().name().toLowerCase();
        return charsetName.contains("utf");
    }

    /**
     * Creates a disabled configuration.
     *
     * @return a disabled configuration
     */
    public static TransparentStatsConfig disabled() {
        return new TransparentStatsConfig(false, DetailLevel.VERBOSE, OutputFormat.CONSOLE);
    }

    /**
     * Creates a configuration for testing with explicit values.
     *
     * @param enabled whether enabled
     * @param detailLevel the detail level
     * @param format the output format
     * @return the configuration
     */
    public static TransparentStatsConfig of(boolean enabled, DetailLevel detailLevel, OutputFormat format) {
        return new TransparentStatsConfig(enabled, detailLevel, format);
    }
}

