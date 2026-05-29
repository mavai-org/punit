package org.mavai.punit.api;

/**
 * Indicates the origin of a probabilistic test's threshold.
 *
 * <p>This enum documents where the {@code minPassRate} threshold came from,
 * enabling traceability between test configuration and business requirements,
 * and determining how hypothesis tests and verdicts should be framed.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @ProbabilisticTest(
 *     samples = 100,
 *     minPassRate = 0.95,
 *     thresholdOrigin = ThresholdOrigin.SLA,
 *     contractRef = "Acme API SLA v3.2 §2.1"
 * )
 * void serviceReturnsValidResponse() { ... }
 * }</pre>
 *
 * <h2>Available Origins</h2>
 * <ul>
 *   <li>{@link #UNSPECIFIED} - Default; threshold origin not documented</li>
 *   <li>{@link #SLA} - Service Level Agreement (external contract)</li>
 *   <li>{@link #SLO} - Service Level Objective (internal target)</li>
 *   <li>{@link #POLICY} - Organizational policy or compliance requirement</li>
 *   <li>{@link #EMPIRICAL} - Derived from baseline measurement</li>
 * </ul>
 *
 * <h2>Impact on Hypothesis Framing</h2>
 * <p>The threshold origin affects how PUnit frames hypothesis tests and verdicts:
 * <ul>
 *   <li>{@code SLA}: "System meets SLA requirement" / "System violates SLA"</li>
 *   <li>{@code SLO}: "System meets SLO target" / "System falls short of SLO"</li>
 *   <li>{@code POLICY}: "System meets policy requirement" / "System violates policy"</li>
 *   <li>{@code EMPIRICAL}: "No degradation from baseline" / "Degradation from baseline"</li>
 *   <li>{@code UNSPECIFIED}: "Success rate meets threshold" / "Success rate below threshold"</li>
 * </ul>
 *
 * @see ProbabilisticTest#thresholdOrigin()
 * @see ProbabilisticTest#contractRef()
 */
// javai-ref: JVI-J8G7TKM — do not remove (resolves in javai-orchestrator)
public enum ThresholdOrigin {

	SLA(true, "Externally agreed normative target (contract/SLA)."),
	SLO(true, "Internally defined normative target (service objective)."),
	POLICY(true, "Normative target derived from policy or governance rule."),
	EMPIRICAL(false, "Empirical reference value derived from experiment/baseline spec."),
	UNSPECIFIED(false, "Non-normative target used for exploratory or temporary checks.");

	private final boolean normative;
	private final String description;

	ThresholdOrigin(boolean normative, String description) {
		this.normative = normative;
		this.description = description;
	}

	/**
	 * Indicates whether this threshold origin represents a normative requirement
	 * (i.e., a target that may be treated as 'verification-capable' only when the
	 * configured statistical parameters are feasible).
	 *
	 * <p>If {@code true} and the test intent is {@code VERIFICATION}, PUnit must
	 * fail fast as MISCONFIGURED when the sample size / confidence / power / MDE
	 * configuration cannot support a statistically valid verification claim.
	 *
	 * <p>If {@code true} and the test intent is {@code SMOKE}, PUnit may execute
	 * with undersized parameters but must label the result as SMOKE and emit an
	 * explicit "sample not sized for verification" caveat to prevent
	 * misinterpretation.
	 */
	public boolean isNormative() {
		return normative;
	}

	public String getDescription() {
		return description;
	}
}
