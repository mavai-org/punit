package org.mavai.punit.decl.internal.run;

import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.decl.internal.model.ContractDeclaration;
import org.mavai.punit.decl.internal.model.CriterionDeclaration;
import org.mavai.punit.decl.internal.model.DeclaredIntent;
import org.mavai.punit.statistics.VerificationFeasibilityEvaluator;

/**
 * Invocation-time sizing: the contract carries the claim, the
 * invocation carries the budget. Resolution order — the per-contract
 * system property, the builder's {@code samples(N)}, then the derived
 * minimum the declared bars require (the smallest supportable N per
 * thresholded criterion via punit's existing feasibility machinery,
 * taking the largest) — guarded by the derivation gate: a silently
 * derived N above 100 is a constructive refusal naming the demanding
 * criterion and both escape hatches. Smoke intent defaults to 5,
 * ungated.
 */
final class Sizing {

    /** The derivation gate: binds only a number nobody typed. */
    static final int DERIVATION_GATE = 100;

    static final int SMOKE_DEFAULT = 5;

    private Sizing() {}

    record Sized(int samples, String provenance) {}

    static Sized resolve(ContractDeclaration declaration, Integer builderSamples) {
        String property = "punit.samples." + declaration.contract();
        String propertyValue = System.getProperty(property);
        if (propertyValue != null) {
            int samples = parse(property, propertyValue);
            return new Sized(samples, "set via -D" + property);
        }
        if (builderSamples != null) {
            if (builderSamples < 1) {
                throw new ContractConfigurationException(
                        ".samples(" + builderSamples + ") must be positive");
            }
            return new Sized(builderSamples, "set via .samples(" + builderSamples + ")");
        }
        if (declaration.intent() == DeclaredIntent.SMOKE) {
            return new Sized(SMOKE_DEFAULT, "smoke-intent default (" + SMOKE_DEFAULT + ")");
        }
        CriterionDeclaration demanding = null;
        int minimum = 0;
        for (CriterionDeclaration criterion : declaration.criteria()) {
            if (criterion.threshold() == null) {
                continue;
            }
            int required = VerificationFeasibilityEvaluator
                    .evaluate(1, criterion.threshold(), declaration.confidence())
                    .minimumSamples();
            if (required > minimum) {
                minimum = required;
                demanding = criterion;
            }
        }
        if (demanding == null) {
            throw new ContractConfigurationException(
                    "nothing sizes this test: no criterion declares a `threshold:` — declare a "
                            + "bar, or size the run explicitly with .samples(N) or -D" + property + "=N");
        }
        if (minimum > DERIVATION_GATE) {
            throw new ContractConfigurationException(
                    "the derived minimum is " + minimum + " samples (criterion '"
                            + demanding.name() + "' at threshold " + demanding.threshold()
                            + ", confidence " + declaration.confidence() + ") — above the "
                            + DERIVATION_GATE + "-sample derivation gate for a silently derived "
                            + "budget. Run it deliberately with .samples(" + minimum + ") or -D"
                            + property + "=" + minimum + ", or declare `intent: smoke`");
        }
        return new Sized(minimum, "derived minimum for criterion '" + demanding.name()
                + "' (threshold " + demanding.threshold() + " at confidence "
                + declaration.confidence() + ")");
    }

    private static int parse(String property, String value) {
        try {
            int samples = Integer.parseInt(value.trim());
            if (samples < 1) {
                throw new NumberFormatException();
            }
            return samples;
        } catch (NumberFormatException error) {
            throw new ContractConfigurationException(
                    "-D" + property + "=" + value + " must be a positive integer");
        }
    }
}
