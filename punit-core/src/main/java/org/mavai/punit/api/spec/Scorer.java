package org.mavai.punit.api.spec;

import java.util.Optional;

/**
 * Reduces a {@link SampleSummary} to a single comparable score for an
 * {@link Experiment} iteration.
 */
@FunctionalInterface
public interface Scorer {
    double score(SampleSummary<?> summary);

    /**
     * The scorer's stable domain name, when it has one. A named scorer
     * is stated in the optimize artefact's additive {@code scorer}
     * field, so downstream consumers can label what the score
     * measures. An ad-hoc lambda carries no name and the field stays
     * absent — the artefact never claims an identity the author did
     * not declare.
     */
    default Optional<String> name() {
        return Optional.empty();
    }

    /**
     * The built-in observed-pass-rate scorer: scores each iteration by
     * its observed pass rate, exactly the rate the artefact's
     * statistics block states. Named {@code observed-pass-rate} in the
     * emitted artefact.
     */
    static Scorer observedPassRate() {
        return new Scorer() {
            @Override
            public double score(SampleSummary<?> summary) {
                return summary.passRate();
            }

            @Override
            public Optional<String> name() {
                return Optional.of("observed-pass-rate");
            }
        };
    }
}
