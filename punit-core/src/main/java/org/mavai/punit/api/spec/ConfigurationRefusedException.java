package org.mavai.punit.api.spec;

import java.util.List;

import org.mavai.punit.statistics.ConfigurationError;

/**
 * A probabilistic test whose configuration was refused before any
 * sample ran (Statistical Companion §5.7.1). It is a configuration
 * problem, not a service failure: the verdict record was written, with
 * the configuration errors in place of a verdict, and the test fails as
 * misconfigured so the refusal cannot be ignored in CI.
 */
public final class ConfigurationRefusedException extends IllegalStateException {

    private final transient List<ConfigurationError> errors;

    public ConfigurationRefusedException(List<ConfigurationError> errors, String message) {
        super(message);
        this.errors = List.copyOf(errors);
    }

    /** Every configuration error, once each, in the fixed reporting order. */
    public List<ConfigurationError> errors() {
        return errors;
    }
}
