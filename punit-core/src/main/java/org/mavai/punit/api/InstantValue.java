package org.mavai.punit.api;

import java.time.Instant;
import java.util.Objects;

/** {@link Instant} scalar factor value. Rendered in ISO-8601 form. */
public record InstantValue(Instant value) implements FactorValue {

    public InstantValue {
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
