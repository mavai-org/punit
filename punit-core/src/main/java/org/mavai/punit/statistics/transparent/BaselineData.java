package org.mavai.punit.statistics.transparent;

import java.time.Instant;

/**
 * Baseline data for statistical explanation.
 *
 * <p>This record holds the baseline information needed to build a statistical
 * explanation without depending on the spec package.
 *
 * @param sourceFile The spec file path/name (e.g., "ShoppingServiceContract.yaml")
 * @param generatedAt When the spec was generated
 * @param baselineSamples Number of samples in the baseline experiment
 * @param baselineSuccesses Number of successes in the baseline experiment
 * @param hasBaselineSpec True if this data comes from a selected baseline spec
 */
public record BaselineData(
        String sourceFile,
        Instant generatedAt,
        int baselineSamples,
        int baselineSuccesses,
        boolean hasBaselineSpec
) {
    /**
     * Canonical constructor - if baselineSamples > 0, hasBaselineSpec is implicitly true.
     */
    public BaselineData {
        if (baselineSamples > 0) {
            hasBaselineSpec = true;
        }
    }
    
    /**
     * Backward-compatible constructor without hasBaselineSpec flag.
     * Defaults to hasBaselineSpec = true if baselineSamples > 0, false otherwise.
     */
    public BaselineData(String sourceFile, Instant generatedAt, int baselineSamples, int baselineSuccesses) {
        this(sourceFile, generatedAt, baselineSamples, baselineSuccesses, baselineSamples > 0);
    }
    
    /**
     * Returns the observed baseline rate.
     */
    public double baselineRate() {
        if (baselineSamples == 0) return 0.0;
        return (double) baselineSuccesses / baselineSamples;
    }

    /**
     * Returns true if this baseline data has empirical samples.
     */
    public boolean hasEmpiricalData() {
        return baselineSamples > 0;
    }
    
    /**
     * @deprecated Use {@link #hasEmpiricalData()} or {@link #hasBaselineSpec()} instead.
     */
    @Deprecated
    public boolean hasData() {
        return baselineSamples > 0;
    }

    /**
     * Creates a baseline with no data (for inline threshold mode).
     */
    public static BaselineData empty() {
        return new BaselineData("(inline configuration)", null, 0, 0, false);
    }
    
    /**
     * Creates a baseline from a selected spec (may or may not have empirical data).
     */
    public static BaselineData fromSpec(String sourceFile, Instant generatedAt, int samples, int successes) {
        return new BaselineData(sourceFile, generatedAt, samples, successes, true);
    }
}

