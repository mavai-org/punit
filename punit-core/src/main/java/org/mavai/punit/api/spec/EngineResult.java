package org.mavai.punit.api.spec;

/**
 * The value produced by {@link Spec#conclude()}. Sealed with two
 * variants: a probabilistic test concludes with a
 * {@link ProbabilisticTestResult}; an experiment concludes with an
 * {@link ExperimentResult}.
 */
public sealed interface EngineResult
        permits ProbabilisticTestResult, ExperimentResult {
}
