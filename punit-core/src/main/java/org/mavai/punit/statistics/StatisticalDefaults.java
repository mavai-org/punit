package org.mavai.punit.statistics;

/**
 * Single source of truth for the framework's default statistical parameters.
 *
 * <h2>Why this class exists</h2>
 * <p>PUnit's statistical tests express the same decision threshold in two equivalent
 * forms: a <strong>confidence level</strong> (e.g. 0.95) and a <strong>significance
 * level</strong> (e.g. 0.05). These are complements: {@code alpha = 1 - confidence}.
 * Several classes across different packages need one or both of these values. Defining
 * them independently risks drift — for example, one class using {@code alpha = 0.05}
 * while another uses {@code confidence = 0.90} — which would make the framework's
 * statistical behaviour internally inconsistent.
 *
 * <p>This class defines the canonical default values exactly once and derives the
 * complementary form, so every consumer in the framework operates at the same
 * significance level.
 *
 * <h2>Quick reference</h2>
 * <table>
 *   <caption>Default statistical parameters</caption>
 *   <tr><th>Parameter</th><th>Value</th><th>Meaning</th></tr>
 *   <tr>
 *     <td>{@link #DEFAULT_CONFIDENCE}</td>
 *     <td>0.95 (95%)</td>
 *     <td>The probability that the confidence interval contains the true pass rate.
 *         Higher values produce wider intervals and require larger sample sizes.</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #DEFAULT_ALPHA}</td>
 *     <td>0.05 (5%)</td>
 *     <td>The maximum acceptable probability of a false positive — rejecting a system
 *         that actually meets the threshold. Derived as {@code 1 - confidence}.</td>
 *   </tr>
 * </table>
 *
 * <h2>How these values are used</h2>
 * <ul>
 *   <li><strong>Threshold derivation</strong> — when a test loads a baseline spec, the
 *       confidence level determines how much the threshold is reduced from the observed
 *       baseline rate (a wider interval gives the system more room for normal variation).</li>
 *   <li><strong>Compliance evidence</strong> — the significance level {@code alpha}
 *       controls the one-sided Wilson score bound used to decide whether a sample is
 *       large enough to support a compliance claim.</li>
 *   <li><strong>Feasibility gate</strong> — for VERIFICATION-intent tests, the
 *       confidence level is used to compute the minimum sample size needed before any
 *       samples execute.</li>
 * </ul>
 *
 * @see ComplianceEvidenceEvaluator
 * @see VerificationFeasibilityEvaluator
 */
public final class StatisticalDefaults {

    /**
     * Default confidence level: 95%.
     *
     * <p>This is the probability that the computed confidence interval contains the
     * true population pass rate. It controls how "sure" the framework needs to be
     * before making a verdict.
     *
     * <p>Raising this value (e.g. to 0.99) would make verdicts more conservative —
     * fewer false positives, but more samples required to pass. Lowering it (e.g. to
     * 0.90) is more permissive but increases the risk of passing a system that does
     * not truly meet the threshold.
     */
    public static final double DEFAULT_CONFIDENCE = 0.95;

    /**
     * Default significance level: 5%.
     *
     * <p>This is the complement of confidence: {@code alpha = 1 - confidence}. It
     * represents the maximum tolerable probability of a <em>Type I error</em> — i.e.
     * concluding that the system fails to meet the threshold when it actually does.
     *
     * <p>This value is derived from {@link #DEFAULT_CONFIDENCE} rather than defined
     * independently, ensuring the two are always consistent.
     */
    public static final double DEFAULT_ALPHA = 1.0 - DEFAULT_CONFIDENCE;

    /**
     * Soundness floor for the configured confidence level: 80%.
     *
     * <p>Configurations whose confidence falls below this floor cannot
     * underwrite a verdict — even a Smoke-intent test should not
     * silently produce results at confidence levels this low. The
     * framework rejects such configurations pre-flight, regardless of
     * declared {@code TestIntent}. This is the only feasibility check
     * that is not intent-gated; every other infeasibility (sample-size
     * adequacy, threshold achievability) is silent under Smoke.
     *
     * <p>Authors who want to assert against this floor in tests can
     * reference the constant directly rather than hard-coding 0.8.
     */
    public static final double SOUNDNESS_FLOOR_CONFIDENCE = 0.80;

    private StatisticalDefaults() {
        // utility class
    }
}
