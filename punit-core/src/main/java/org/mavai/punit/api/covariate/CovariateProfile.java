package org.mavai.punit.api.covariate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The resolved values of a service contract's declared covariates at one
 * point in time.
 *
 * <p>A profile is the snapshot of environmental conditions under which
 * a measurement was taken, or under which a test is now running. Each
 * entry maps a covariate's {@link Covariate#name() name} to its
 * resolved label — the partition label for partitioned built-ins, the
 * matching {@code HH:mm/Nh} period for time-of-day, the IANA string
 * for timezone, or whatever a custom resolver returned.
 *
 * <p>The profile is part of a baseline's identity. Comparing today's
 * test (resolved profile {@code dow=remainder, region=GB}) against a
 * baseline measured under {@code dow=WEEKEND, region=FR} is comparing
 * apples to oranges; baseline matching uses the profile to find the
 * right baseline (or surface a misalignment when none matches).
 *
 * <p>An empty profile (the result of a service contract declaring no
 * covariates) is a valid value — the baseline's identity reduces to
 * the service contract alone.
 *
 * <p>Insertion order is preserved so callers iterating the profile
 * see entries in the order their declarations were resolved.
 */
public final class CovariateProfile {

    private static final CovariateProfile EMPTY = new CovariateProfile(Map.of());

    private final Map<String, String> values;

    private CovariateProfile(Map<String, String> values) {
        this.values = values;
    }

    /**
     * @return the singleton empty profile
     */
    public static CovariateProfile empty() {
        return EMPTY;
    }

    /**
     * @param values resolved name→label entries; insertion order is
     *               preserved
     * @return a new profile holding a defensive copy of the input
     */
    public static CovariateProfile of(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            return EMPTY;
        }
        // LinkedHashMap defensive copy preserves insertion order; the
        // wrapper makes the result unmodifiable.
        Map<String, String> copy = new LinkedHashMap<>(values.size());
        for (Map.Entry<String, String> e : values.entrySet()) {
            Objects.requireNonNull(e.getKey(), "covariate name");
            Objects.requireNonNull(e.getValue(), "covariate value for " + e.getKey());
            copy.put(e.getKey(), e.getValue());
        }
        return new CovariateProfile(java.util.Collections.unmodifiableMap(copy));
    }

    /**
     * @return the resolved label for {@code covariateName}, or empty
     *         when the profile contains no such entry
     */
    public Optional<String> get(String covariateName) {
        return Optional.ofNullable(values.get(covariateName));
    }

    /**
     * @return the immutable underlying map; preserves resolution
     *         order
     */
    public Map<String, String> values() {
        return values;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public int size() {
        return values.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CovariateProfile other)) return false;
        return values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return "CovariateProfile" + values;
    }
}
