package org.mavai.punit.api.covariate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The alignment between a probabilistic test's observed (current
 * run's) covariate profile and the matched baseline's covariate
 * profile. The structured value flows to verdict-text renderers,
 * HTML report emitters, and JSON sinks.
 *
 * <p>Empty profiles (the service contract declared no covariates) produce
 * the {@link #aligned() aligned} state with no misalignments. A
 * baseline profile that matches the observed profile component-
 * by-component also produces aligned. Any divergence — different
 * value, key present in one and not the other — produces a
 * {@link Mismatch} entry and {@code aligned == false}.
 *
 * @param observed       the run's resolved profile at sample time
 * @param baseline       the matched baseline's profile
 *                       ({@link CovariateProfile#empty()} when no
 *                       baseline was matched, or when the matched
 *                       baseline carried no covariate data)
 * @param aligned        derived: {@code true} iff every key in
 *                       {@code observed} ∪ {@code baseline} agrees
 * @param mismatches     derived: per-key differences between the
 *                       two profiles, in baseline-key insertion
 *                       order followed by observed-only keys
 */
public record CovariateAlignment(
        CovariateProfile observed,
        CovariateProfile baseline,
        boolean aligned,
        List<Mismatch> mismatches) {

    /**
     * One key on which observed and baseline disagree.
     *
     * @param covariateKey the covariate name
     * @param observed     the run-time value, or {@code null} when
     *                     the key is present only on the baseline
     * @param baseline     the baseline value, or {@code null} when
     *                     the key is present only on the observed
     *                     profile
     */
    public record Mismatch(String covariateKey, String observed, String baseline) {

        public Mismatch {
            Objects.requireNonNull(covariateKey, "covariateKey");
        }
    }

    public CovariateAlignment {
        Objects.requireNonNull(observed, "observed");
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(mismatches, "mismatches");
        mismatches = List.copyOf(mismatches);
    }

    /**
     * @return the canonical "no covariates declared, no baseline
     *         matched" alignment — both profiles empty, aligned, no
     *         mismatches
     */
    public static CovariateAlignment none() {
        return new CovariateAlignment(
                CovariateProfile.empty(), CovariateProfile.empty(), true, List.of());
    }

    /**
     * Computes alignment from the two profiles. Aligned when the
     * baseline is empty (nothing to compare against) or when every
     * key on either side has equal values.
     */
    public static CovariateAlignment compute(
            CovariateProfile observed, CovariateProfile baseline) {
        Objects.requireNonNull(observed, "observed");
        Objects.requireNonNull(baseline, "baseline");
        if (baseline.isEmpty()) {
            // No matched baseline, or the matched baseline had no
            // covariate stamping. Nothing to misalign against.
            return new CovariateAlignment(observed, baseline, true, List.of());
        }
        List<Mismatch> mismatches = new ArrayList<>();
        // Compare baseline-side keys first, then any observed-only.
        Set<String> seen = new TreeSet<>();
        for (var entry : baseline.values().entrySet()) {
            seen.add(entry.getKey());
            String observedValue = observed.get(entry.getKey()).orElse(null);
            if (!entry.getValue().equals(observedValue)) {
                mismatches.add(new Mismatch(
                        entry.getKey(), observedValue, entry.getValue()));
            }
        }
        for (var entry : observed.values().entrySet()) {
            if (seen.add(entry.getKey())) {
                // Observed key absent from baseline — also a mismatch.
                mismatches.add(new Mismatch(
                        entry.getKey(), entry.getValue(), null));
            }
        }
        return new CovariateAlignment(
                observed, baseline, mismatches.isEmpty(), List.copyOf(mismatches));
    }
}
