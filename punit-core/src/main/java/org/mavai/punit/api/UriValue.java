package org.mavai.punit.api;

import java.net.URI;
import java.util.Objects;

/** {@link URI} scalar factor value. Rendered as the URI's string form. */
public record UriValue(URI value) implements FactorValue {

    public UriValue {
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
