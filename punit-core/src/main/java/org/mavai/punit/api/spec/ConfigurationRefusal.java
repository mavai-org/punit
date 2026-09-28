package org.mavai.punit.api.spec;

import java.util.Objects;

import org.mavai.punit.statistics.ConfigurationError;

/**
 * One reason a test configuration is refused before any sample runs
 * (Statistical Companion §5.7.1): the named code and a sentence saying
 * which part of the configuration is invalid and how to correct it.
 *
 * @param code   the configuration error
 * @param reason what is wrong, in terms of the configuration
 */
public record ConfigurationRefusal(ConfigurationError code, String reason) {

    public ConfigurationRefusal {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(reason, "reason");
    }
}
