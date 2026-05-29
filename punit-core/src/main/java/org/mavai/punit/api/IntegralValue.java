package org.mavai.punit.api;

/** Integer-valued scalar factor. Covers all boxed and primitive integral widths. */
public record IntegralValue(long value) implements FactorValue {

    @Override
    public String canonical() {
        return Long.toString(value);
    }

    @Override
    public Object yamlValue() {
        return value;
    }
}
