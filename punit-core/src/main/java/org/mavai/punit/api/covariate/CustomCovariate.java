package org.mavai.punit.api.covariate;

import java.util.Objects;

/**
 * A custom covariate. The framework knows the name and the category;
 * the resolution mechanism (how the value is captured at sample time)
 * is supplied separately by the authoring code.
 *
 * @param name     the covariate's key; non-blank, filename-safe
 * @param category the taxonomy category; non-null. {@code CONFIGURATION}
 *                 covariates are hard-gated at baseline matching;
 *                 others are soft-matched.
 */
public record CustomCovariate(String name, CovariateCategory category)
        implements Covariate {

    public CustomCovariate {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(category, "category");
        if (name.isBlank()) {
            throw new IllegalArgumentException(
                    "custom covariate name must be non-blank");
        }
    }
}
