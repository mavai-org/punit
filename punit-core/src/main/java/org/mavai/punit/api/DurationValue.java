package org.mavai.punit.api;

import java.time.Duration;
import java.util.Objects;

/** {@link Duration} scalar factor value. Rendered in ISO-8601 form. */
public record DurationValue(Duration value) implements FactorValue {

    public DurationValue {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String canonical() {
        return StringValue.jsonQuote(value.toString());
    }

    @Override
    public Object yamlValue() {
        return value.toString();
    }
}
