package org.mavai.punit.api;

/**
 * Declares the intent of a probabilistic test.
 *
 * <p>PUnit uses the intent to decide how strict it should be when interpreting results.
 *
 * <h3>{@link #VERIFICATION}</h3>
 * <p><strong>Evidential.</strong> This mode is used when the test is meant to make a
 * confidence-backed claim that the system meets the configured target.
 *
 * <ul>
 *   <li>PUnit checks the configuration up front. If the chosen sample size and settings cannot
 *       support a verification-grade result, the test fails fast before executing any samples
 *       (via {@link org.junit.jupiter.api.extension.ExtensionConfigurationException}).</li>
 *   <li>PASS means the observed results meet the target under PUnit's verification rules.</li>
 *   <li>FAIL means the observed results fall below the target under those rules.</li>
 * </ul>
 *
 * <h3>{@link #SMOKE}</h3>
 * <p><strong>Sentinel.</strong> This mode is a lightweight early-warning check.
 *
 * <ul>
 *   <li>PUnit does not enforce feasibility. Undersized configurations are allowed.</li>
 *   <li>PASS is not proof of meeting the target; it simply means no obvious regression was
 *       detected at the chosen sample size.</li>
 *   <li>FAIL is a signal to investigate (e.g., a likely regression), not a strong statistical
 *       conclusion at small sample sizes.</li>
 * </ul>
 *
 * <p>When the threshold origin is normative (for example SLA/SLO/policy), SMOKE verdicts include
 * an explicit caveat such as "sample not sized for verification" to prevent misinterpretation.
 *
 * <p>The default intent is {@link #VERIFICATION}. Developers must explicitly opt into SMOKE when
 * they want a lightweight check rather than an evidential claim.
 *
 * @see ProbabilisticTest#intent()
 */
// javai-ref: JVI-Q3H5YYY — do not remove (resolves in javai-orchestrator)
// javai-ref: JVI-WKHRGXJ — do not remove (resolves in javai-orchestrator)
public enum TestIntent {
    /**
     * Evidential intent. Use this when the test is expected to support a confidence-backed
     * claim that the system meets the configured target.
     */
    VERIFICATION,

    /**
     * Sentinel intent. Use this for lightweight regression and early-warning checks where
     * verification would be too expensive or the configuration is intentionally small.
     */
    SMOKE
}