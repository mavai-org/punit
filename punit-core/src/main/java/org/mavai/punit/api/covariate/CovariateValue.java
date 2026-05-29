package org.mavai.punit.api.covariate;

import java.util.Objects;

/**
 * A covariate value.
 *
 * <p>Covariate values are captured during experiments and compared during
 * probabilistic tests to determine baseline conformance.
 */
public sealed interface CovariateValue permits CovariateValue.StringValue {

    /**
     * Returns the canonical string representation for storage and hashing.
     *
     * <p>This format is used in:
     * <ul>
     *   <li>YAML spec files</li>
     *   <li>Covariate profile hash computation</li>
     *   <li>Reporting output</li>
     * </ul>
     *
     * @return the canonical string representation
     */
    String toCanonicalString();

    /**
     * Simple string value (e.g., "WEEKEND", "08:00/2h", "Europe/London").
     *
     * <p>Used for all covariate values.
     */
    record StringValue(String value) implements CovariateValue {

        public StringValue {
            Objects.requireNonNull(value, "value must not be null");
        }

        @Override
        public String toCanonicalString() {
            return value;
        }
    }
}
