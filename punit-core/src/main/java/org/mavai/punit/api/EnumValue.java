package org.mavai.punit.api;

import java.util.Objects;

/**
 * Enum scalar factor value. Captured as the declaring class name plus
 * the constant name so the value survives a YAML round-trip without
 * the enum class being on the reader's classpath.
 */
public record EnumValue(String declaringClass, String name) implements FactorValue {

    public EnumValue {
        Objects.requireNonNull(declaringClass, "declaringClass");
        Objects.requireNonNull(name, "name");
    }

    @Override
    public String canonical() {
        return StringValue.jsonQuote(name);
    }

    @Override
    public Object yamlValue() {
        return name;
    }
}
