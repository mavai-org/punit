package org.mavai.punit.decl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registers a method of the bindings class as a named stepper — the
 * algorithm an {@code optimizations:} entry's {@code stepper:} key
 * resolves to. The method's parameter list <strong>is</strong> the
 * entry's {@code stepper-config:} schema (kebab-case keys map to
 * camelCase parameters, exactly as a {@link BindingFactory} factory's),
 * and the method returns the constructed
 * {@link org.mavai.punit.api.spec.FactorsStepper} — any state the
 * algorithm keeps lives in the returned stepper's closure.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Stepper {

    /** The stepper's registry name — unique within the stepper registry. */
    String value();
}
