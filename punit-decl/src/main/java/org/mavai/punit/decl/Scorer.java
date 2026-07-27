package org.mavai.punit.decl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registers a method of the bindings class as a named scorer — the
 * judge an {@code optimizations:} entry's {@code scorer:} key resolves
 * to. A no-argument method returning an
 * {@link org.mavai.punit.api.spec.Scorer}. The default scorer,
 * {@code pass-rate}, is built in and needs no registration.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Scorer {

    /** The scorer's registry name — unique within the scorer registry. */
    String value();
}
