package org.mavai.punit.api.spec;

/**
 * Reduces a {@link SampleSummary} to a single comparable score for an
 * {@link Experiment} iteration.
 */
@FunctionalInterface
public interface Scorer {
    double score(SampleSummary<?> summary);
}
