package org.mavai.punit.statistics;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Represents a statistically-derived threshold for probabilistic testing.
 *
 * <p>The threshold p_threshold is the minimum observed pass rate required for a test
 * to pass. It is derived from the baseline experimental data using one of three
 * {@link OperationalApproach}es.
 *
 * <p>For the Sample-Size-First approach (most common):
 * <pre>
 *   p_threshold = Wilson one-sided lower bound at confidence (1-α)
 * </pre>
 *
 * <p>This ensures that if the true rate has not degraded from the baseline,
 * the probability of a false positive (test failing when system is fine) is at most α.
 *
 * <p><b>The binding decision artefact</b> (statistical companion §3.4): for a
 * baseline-derived test the decision is discrete — the integer cutoff
 * {@code c = ⌈n_test · p_threshold⌉}, with PASS iff the raw observed success
 * count {@code K ≥ c}. The real-valued threshold, the displayed rate
 * {@code c / n_test}, and the achieved size (the exact probability, under the
 * effective baseline rate, of a false degradation signal:
 * {@code P(K < c)}) are the report obligations that accompany a conformant
 * verdict. Sample-size-first derivations carry all three; threshold-first
 * derivations answer a different question (the implied confidence of a given
 * threshold) and carry none.
 *
 * @param value The derived threshold value p_threshold ∈ [0, 1]
 * @param approach The operational approach used for derivation
 * @param context The derivation context (baseline data, sample sizes, confidence)
 * @param isStatisticallySound True if the derivation produces a reliable threshold;
 *                              false if the implied confidence is too low (&lt; 80%)
 * @param cutoff The integer cutoff {@code c = ⌈n_test · value⌉} — the binding
 *               decision artefact; present on sample-size-first derivations
 * @param achievedSize The exact achieved size {@code P(K < c)} under the
 *                     effective baseline rate; present on sample-size-first
 *                     derivations
 *
 * @see OperationalApproach
 */
public record DerivedThreshold(
    double value,
    OperationalApproach approach,
    DerivationContext context,
    boolean isStatisticallySound,
    OptionalInt cutoff,
    OptionalDouble achievedSize
) {
    /**
     * Validates the threshold parameters.
     */
    public DerivedThreshold {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                "Threshold value must be in [0, 1], got: " + value);
        }
        if (approach == null) {
            throw new IllegalArgumentException("Approach must not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null");
        }
        if (cutoff == null || achievedSize == null) {
            throw new IllegalArgumentException("Decision artefacts must not be null");
        }
    }

    /**
     * Convenience constructor for thresholds carrying no discrete decision
     * artefacts (threshold-first derivations, hand-built values).
     */
    public DerivedThreshold(
            double value, OperationalApproach approach, DerivationContext context,
            boolean isStatisticallySound) {
        this(value, approach, context, isStatisticallySound,
                OptionalInt.empty(), OptionalDouble.empty());
    }

    /**
     * Convenience constructor for statistically sound thresholds.
     */
    public DerivedThreshold(double value, OperationalApproach approach, DerivationContext context) {
        this(value, approach, context, true);
    }

    /**
     * The displayed rate {@code c / n_test} — the integer cutoff expressed
     * as a rate, the figure a conformant report shows alongside the cutoff
     * (companion §3.4). Present exactly when {@link #cutoff()} is.
     */
    public OptionalDouble displayedRate() {
        if (cutoff.isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of((double) cutoff.getAsInt() / context.testSamples());
    }

    /**
     * Returns the gap between the baseline rate and the derived threshold.
     *
     * <p>This gap accounts for:
     * <ul>
     *   <li>Uncertainty in the baseline estimate</li>
     *   <li>Increased variance with smaller test sample</li>
     *   <li>Desired confidence level</li>
     * </ul>
     *
     * @return baselineRate - threshold (positive if threshold is below baseline)
     */
    public double gapFromBaseline() {
        return context.baselineRate() - value;
    }
}
