package org.mavai.punit.api.spec;

import java.util.Objects;

/**
 * One configuration's outcome from an EXPLORE run — its factor
 * record paired with the {@link SampleSummary} the engine produced
 * for it, plus the configuration's planned sample count. Yielded in
 * declaration-order from {@link Experiment#perConfigSummaries()}
 * after an explore run completes.
 *
 * <p>Diagnostic surface, not an authoring surface. Consumed by the
 * EXPLORE artefact emitter; user-side experiment code does not
 * construct or read these values directly.
 *
 * @param factors the factor record for this configuration
 * @param summary the engine's per-sample aggregate for this
 *                configuration
 * @param samplesPlanned the configuration's planned sample count —
 *                       what the spec asked for, before any early
 *                       termination. {@code summary.total()} reports
 *                       what actually ran.
 * @param <FT> factor record type
 * @param <OT> service contract output type
 */
public record PerConfigSummary<FT, OT>(
        FT factors,
        SampleSummary<OT> summary,
        int samplesPlanned) {

    public PerConfigSummary {
        Objects.requireNonNull(factors, "factors");
        Objects.requireNonNull(summary, "summary");
        if (samplesPlanned < 0) {
            throw new IllegalArgumentException(
                    "samplesPlanned must be non-negative, got " + samplesPlanned);
        }
    }
}
