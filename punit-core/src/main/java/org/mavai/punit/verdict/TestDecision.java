package org.mavai.punit.verdict;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

import org.mavai.punit.api.spec.VerdictComposition;
import org.mavai.punit.statistics.ConfigurationError;
import org.mavai.punit.statistics.DecisionRule;

/**
 * The decision behind a verdict (Statistical Companion 1.5.0, §10.2):
 * what refused the configuration, or which rule decided the test and
 * what triggered a FAIL or an INCONCLUSIVE, with the Type-I envelopes
 * split by direction.
 *
 * @param configurationErrors the codes that refused the configuration
 *                            before any sample ran, in the fixed
 *                            reporting order; empty for a test that ran.
 *                            A refused record has no verdict value
 * @param refusalDetail       what each refused part was, for the record
 * @param decisionRule        the rule, when one rule decided the whole
 *                            test; empty when several did (each is stated
 *                            where it decided) or none did
 * @param triggering          the criteria and latency constraints whose
 *                            verdict is the test's, for a FAIL or an
 *                            INCONCLUSIVE
 * @param falseComplianceEnvelope        the sum of alpha over the
 *                            compliance decisions; empty when none
 * @param falseDegradationSignalEnvelope the sum of alpha over the
 *                            regression decisions; empty when none
 */
public record TestDecision(
        List<ConfigurationError> configurationErrors,
        Optional<String> refusalDetail,
        Optional<DecisionRule> decisionRule,
        List<VerdictComposition.Trigger> triggering,
        OptionalDouble falseComplianceEnvelope,
        OptionalDouble falseDegradationSignalEnvelope) {

    /** No decision information: a record built without one. */
    public static final TestDecision NONE = new TestDecision(
            List.of(), Optional.empty(), Optional.empty(), List.of(),
            OptionalDouble.empty(), OptionalDouble.empty());

    public TestDecision {
        configurationErrors = List.copyOf(Objects.requireNonNull(configurationErrors, "configurationErrors"));
        Objects.requireNonNull(refusalDetail, "refusalDetail");
        Objects.requireNonNull(decisionRule, "decisionRule");
        triggering = List.copyOf(Objects.requireNonNull(triggering, "triggering"));
        Objects.requireNonNull(falseComplianceEnvelope, "falseComplianceEnvelope");
        Objects.requireNonNull(falseDegradationSignalEnvelope, "falseDegradationSignalEnvelope");
    }

    /** Whether the configuration was refused before any sample ran. */
    public boolean refused() {
        return !configurationErrors.isEmpty();
    }
}
