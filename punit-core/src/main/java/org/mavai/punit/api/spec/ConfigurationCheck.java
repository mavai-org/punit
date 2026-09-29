package org.mavai.punit.api.spec;

import java.util.Map;
import java.util.Optional;

import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.criterion.CriterionPosture;

/**
 * What a {@link Criterion} is shown before any sample runs, to say
 * whether its part of the configuration is valid
 * ({@link Criterion#configurationRefusals(ConfigurationCheck)}).
 *
 * @param <S> the baseline statistics type the criterion consumes
 */
public interface ConfigurationCheck<S extends BaselineStatistics> {

    /** The test's planned sample size. */
    int plannedSamples();

    /** The test's declared intent. */
    TestIntent intent();

    /** The contract's per-criterion postures, keyed by criterion id. */
    Map<String, CriterionPosture> criterionPostures();

    /** The resolved baseline, for an empirical criterion that has one. */
    Optional<S> baseline();
}
