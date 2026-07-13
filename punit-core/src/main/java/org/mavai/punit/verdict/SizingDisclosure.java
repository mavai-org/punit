package org.mavai.punit.verdict;

import java.util.LinkedHashMap;
import java.util.Map;

import org.mavai.punit.api.ThresholdOrigin;
import org.mavai.punit.statistics.RiskDrivenSizingCalculator;
import org.mavai.punit.statistics.StatisticalDefaults;

/**
 * Sizing-transparency facts recorded with the verdict.
 *
 * <p>A report shows verdicts and statistics, but is silent about the deal
 * the operator struck when sizing the run: which operational approach
 * shaped the design, what a smaller-than-baseline sample count cost in
 * sensitivity, and what it saved in time and tokens. This class computes
 * those facts at verdict-build time — the sensitivity figure through the
 * statistics package's sizing calculator — and records them as free-form
 * environment entries, so the verdict XML schema is unchanged and every
 * renderer formats already-computed values.
 *
 * <p>The approach entry (with the parameters the run declared) is present
 * on every verdict. The downsizing pair — the detectable rate at the run's
 * size and the estimated savings versus a baseline-sized run — is present
 * iff the run was sized below the resolved baseline's own sampling size;
 * the token half of the savings is present iff the run recorded token
 * costs. The entry vocabulary is shared across the framework family, and
 * deliberately leaves room for the confidence-first parameters (tolerated
 * rate, target power, computed sample size) that a risk-driven authoring
 * surface will declare: those entries extend this disclosure additively.
 */
// javai-ref: JVI-RX30FM8 — do not remove (resolves in javai-orchestrator)
final class SizingDisclosure {

    static final String APPROACH_KEY = "sizing-approach";
    static final String DECLARED_SAMPLES_KEY = "sizing-declared-samples";
    static final String DECLARED_CONFIDENCE_KEY = "sizing-declared-confidence";
    static final String DECLARED_MIN_PASS_RATE_KEY = "sizing-declared-min-pass-rate";
    static final String BASELINE_SAMPLES_KEY = "sizing-baseline-samples";
    static final String DETECTABLE_RATE_KEY = "sizing-detectable-rate";
    static final String DETECTABLE_POWER_KEY = "sizing-detectable-power";
    static final String SAVED_FRACTION_KEY = "sizing-saved-fraction";
    static final String TIME_SAVED_MS_KEY = "sizing-time-saved-ms";
    static final String TOKENS_SAVED_KEY = "sizing-tokens-saved";

    private static final RiskDrivenSizingCalculator SIZING_CALCULATOR =
            new RiskDrivenSizingCalculator();

    private SizingDisclosure() {
    }

    /**
     * Computes the sizing-transparency entries one verdict carries.
     *
     * <p>An empirical threshold origin marks the sample-size-first
     * approach (the run declared its sample count and confidence; the
     * acceptance bar was derived at that size); any other origin —
     * including none — marks threshold-first (the run declared its bar
     * outright). The confidence-first (risk-driven) arm is disclosed by
     * the authoring surface that declares it, not inferred here.
     *
     * @param thresholdOrigin where the run's threshold came from; may be null
     * @param plannedSamples the sample count the run was sized at
     * @param samplesExecuted the sample count actually executed
     * @param minPassRate the run's acceptance threshold
     * @param resolvedConfidence the confidence the run resolved
     * @param baselineSamples the resolved baseline's sampling size, or null
     *                        when no baseline resolved
     * @param baselineRate the resolved baseline's effective rate, or null
     *                     when no baseline resolved
     * @param elapsedMs the run's recorded wall-clock time
     * @param tokensConsumed the run's recorded token cost (0 = unrecorded)
     * @return the entries, in stable insertion order
     */
    static Map<String, String> entries(
            ThresholdOrigin thresholdOrigin,
            int plannedSamples,
            int samplesExecuted,
            double minPassRate,
            double resolvedConfidence,
            Integer baselineSamples,
            Double baselineRate,
            long elapsedMs,
            long tokensConsumed) {
        Map<String, String> entries = new LinkedHashMap<>();

        boolean sampleSizeFirst = thresholdOrigin == ThresholdOrigin.EMPIRICAL;
        entries.put(APPROACH_KEY, sampleSizeFirst ? "sample-size-first" : "threshold-first");
        entries.put(DECLARED_SAMPLES_KEY, Integer.toString(plannedSamples));
        if (sampleSizeFirst) {
            entries.put(DECLARED_CONFIDENCE_KEY, Double.toString(resolvedConfidence));
        } else {
            entries.put(DECLARED_MIN_PASS_RATE_KEY, Double.toString(minPassRate));
        }

        if (downsized(plannedSamples, baselineSamples, baselineRate) && samplesExecuted > 0) {
            double targetPower = StatisticalDefaults.DEFAULT_TARGET_POWER;
            double detectableRate = SIZING_CALCULATOR.detectableRate(
                    plannedSamples, baselineRate, resolvedConfidence, targetPower);
            entries.put(BASELINE_SAMPLES_KEY, Integer.toString(baselineSamples));
            entries.put(DETECTABLE_RATE_KEY, Double.toString(detectableRate));
            entries.put(DETECTABLE_POWER_KEY, Double.toString(targetPower));

            int savedSamples = baselineSamples - plannedSamples;
            double savedFraction = (double) savedSamples / baselineSamples;
            entries.put(SAVED_FRACTION_KEY, Double.toString(savedFraction));
            long timeSavedMs = Math.round(
                    (double) elapsedMs / samplesExecuted * savedSamples);
            entries.put(TIME_SAVED_MS_KEY, Long.toString(timeSavedMs));
            if (tokensConsumed > 0) {
                long tokensSaved = Math.round(
                        (double) tokensConsumed / samplesExecuted * savedSamples);
                entries.put(TOKENS_SAVED_KEY, Long.toString(tokensSaved));
            }
        }

        return entries;
    }

    /**
     * The downsizing trade exists iff the run was sized below the resolved
     * baseline's own sampling size, and the baseline's rate leaves a
     * degradation to detect (a perfect or empty baseline does not).
     */
    private static boolean downsized(
            int plannedSamples, Integer baselineSamples, Double baselineRate) {
        return baselineSamples != null
                && baselineRate != null
                && plannedSamples > 0
                && plannedSamples < baselineSamples
                && baselineRate > 0.0
                && baselineRate < 1.0;
    }
}
