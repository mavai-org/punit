package org.mavai.punit.decl.internal.model;

import java.util.List;

/**
 * The parsed {@code latency:} block: explicit per-percentile millisecond
 * ceilings, or the percentiles whose bounds derive from a measured
 * baseline — mutually exclusive shapes, each with an optional
 * confidence and the provenance keys.
 *
 * @param ceilings the explicit ceilings in percentile order; empty for
 *     the empirical shape
 * @param empirical the baseline-derived percentiles in percentile
 *     order; empty for the explicit shape
 * @param confidence the derivation confidence (empirical shape) or
 *     recorded provenance (explicit shape), or {@code null}
 * @param thresholdOrigin the bar's provenance category, or {@code null}
 * @param contractRef the provenance document reference, or {@code null}
 */
public record LatencyDeclaration(
        List<PercentileCeiling> ceilings,
        List<String> empirical,
        Double confidence,
        String thresholdOrigin,
        String contractRef) {

    /** One explicit ceiling: a percentile and its millisecond bound. */
    public record PercentileCeiling(String percentile, int millis) {}

    public LatencyDeclaration {
        ceilings = List.copyOf(ceilings);
        empirical = List.copyOf(empirical);
    }
}
