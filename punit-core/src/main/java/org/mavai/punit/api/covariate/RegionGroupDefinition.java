package org.mavai.punit.api.covariate;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A named group of region codes forming a single partition for the region covariate.
 *
 * <p>Regions within a group are considered statistically equivalent. Region codes
 * are normalized to uppercase for consistent matching.
 *
 * @param regions the region codes in this group (non-empty, uppercase, immutable)
 * @param label the partition label (non-blank)
 */
public record RegionGroupDefinition(Set<String> regions, String label) {

    public RegionGroupDefinition {
        Objects.requireNonNull(regions, "regions must not be null");
        Objects.requireNonNull(label, "label must not be null");
        if (regions.isEmpty()) {
            throw new IllegalArgumentException("regions must not be empty");
        }
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        regions = regions.stream()
                .map(String::toUpperCase)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Returns true if this group contains the given region code (case-insensitive).
     *
     * @param regionCode the region code to check
     * @return true if the region is in this group
     */
    public boolean contains(String regionCode) {
        return regions.contains(regionCode.toUpperCase());
    }
}
