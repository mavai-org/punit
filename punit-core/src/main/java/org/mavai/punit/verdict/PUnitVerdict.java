package org.mavai.punit.verdict;

/**
 * The three-state statistical verdict determined by PUnit's analysis.
 *
 * <ul>
 *   <li>{@link #PASS} — Insufficient evidence to reject H₀; no statistically significant
 *       divergence from the baseline detected.</li>
 *   <li>{@link #FAIL} — H₀ rejected; sufficient statistical evidence of a divergence from
 *       the baseline. This is the call to action.</li>
 *   <li>{@link #INCONCLUSIVE} — Covariate misalignment; the statistical analysis cannot be
 *       relied upon because test conditions do not match baseline conditions.</li>
 * </ul>
 *
 * <p>This is independent of the JUnit pass/fail state. JUnit red + PUnit PASS is a legitimate
 * combination meaning "samples failed, but within the expected statistical range — no breach."
 */
public enum PUnitVerdict {
    PASS,
    FAIL,
    INCONCLUSIVE
}
