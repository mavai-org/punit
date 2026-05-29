package org.mavai.punit.api.covariate;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * A region covariate. Each region is an ISO 3166-1 alpha-2 country
 * code (e.g. {@code "FR"}, {@code "DE"}). Regions within a single
 * inner set match the same baseline; regions falling outside every
 * declared partition form an implicit remainder partition.
 *
 * @param partitions the region-set partitions; non-empty, each set
 *                   non-empty, each region a 2-letter alpha code
 */
public record RegionCovariate(List<Set<String>> partitions)
        implements Covariate {

    private static final String NAME = "region";

    public RegionCovariate {
        Objects.requireNonNull(partitions, "partitions");
        if (partitions.isEmpty()) {
            throw new IllegalArgumentException(
                    "region covariate requires at least one partition");
        }
        for (int i = 0; i < partitions.size(); i++) {
            Set<String> partition = partitions.get(i);
            if (partition == null || partition.isEmpty()) {
                throw new IllegalArgumentException(
                        "region partition at index " + i
                                + " must be non-empty");
            }
            for (String code : partition) {
                if (!isValidAlpha2(code)) {
                    throw new IllegalArgumentException(
                            "region '" + code + "' in partition at index "
                                    + i + " is not a 2-letter ISO 3166-1 alpha-2 code");
                }
            }
        }
        partitions = List.copyOf(
                partitions.stream()
                        .map(p -> p.stream()
                                .map(s -> s.toUpperCase(Locale.ROOT))
                                .collect(java.util.stream.Collectors.toUnmodifiableSet()))
                        .toList());
    }

    @Override
    public CovariateCategory category() {
        return CovariateCategory.INFRASTRUCTURE;
    }

    @Override
    public String name() {
        return NAME;
    }

    private static boolean isValidAlpha2(String code) {
        if (code == null || code.length() != 2) {
            return false;
        }
        return Character.isLetter(code.charAt(0)) && Character.isLetter(code.charAt(1));
    }
}
