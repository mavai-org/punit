package org.mavai.punit.api.covariate;

/**
 * A timezone covariate. The system timezone (resolved at sample time)
 * is captured as an exact-string identity covariate — no partitioning,
 * no implicit remainder. A baseline measured under
 * {@code Europe/Zurich} matches only tests under {@code Europe/Zurich}.
 */
public record TimezoneCovariate() implements Covariate {

    private static final String NAME = "timezone";

    @Override
    public CovariateCategory category() {
        return CovariateCategory.INFRASTRUCTURE;
    }

    @Override
    public String name() {
        return NAME;
    }
}
