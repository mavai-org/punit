package org.mavai.punit.verdict;

import java.util.ArrayList;
import java.util.List;

import org.mavai.punit.statistics.ComplianceRule;
import org.mavai.punit.statistics.Methodology;
import org.mavai.punit.statistics.VerificationFeasibilityEvaluator;

/**
 * The intent-aware caveats of a SMOKE test against a declared (normative)
 * requirement (Statistical Companion §5.7.4). Under SMOKE such a
 * requirement is decided by {@code compliance/smoke-exact-binomial}: a
 * FAIL is evidence that the service falls short; a PASS states only that
 * the run found no such evidence. The caveats are stated for the planned
 * design size, which the rule decides at.
 */
public final class SmokeCaveats {

    private SmokeCaveats() {
    }

    /**
     * The caveats for a SMOKE design of {@code plannedSamples} against
     * {@code requirement} at {@code confidence}; empty for a requirement
     * outside (0, 1) or a design with no samples.
     */
    public static List<String> forNormative(int plannedSamples, double requirement, double confidence) {
        List<String> caveats = new ArrayList<>(2);
        if (plannedSamples <= 0 || Double.isNaN(requirement)
                || requirement <= 0.0 || requirement >= 1.0) {
            return caveats;
        }
        var feasibility = VerificationFeasibilityEvaluator.evaluate(
                plannedSamples, requirement, confidence);
        if (!feasibility.feasible()) {
            caveats.add(String.format(
                    "This size cannot demonstrate compliance (N = %d, need at least %d). "
                            + "A PASS at this size is not evidence of compliance: it shows only "
                            + "that the run found no evidence of falling short of the requirement.",
                    plannedSamples, feasibility.minimumSamples()));
        } else {
            caveats.add("PASS is possible at this size. Consider setting "
                    + "intent = VERIFICATION for evidential strength, and check the "
                    + "power statement.");
        }
        double alpha = Methodology.alphaFromConfidence(confidence);
        if (ComplianceRule.smokeRequiredPass(requirement, plannedSamples, alpha) == 0) {
            caveats.add("FAIL not possible at this size; this result carries no evidence "
                    + "about the service.");
        }
        return caveats;
    }
}
