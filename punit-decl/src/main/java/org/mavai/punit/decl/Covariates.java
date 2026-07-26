package org.mavai.punit.decl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registers a method of the bindings class as a service's computed
 * covariate feed: a no-argument method returning
 * {@code Map<String, String>}, invoked once per run when the named
 * service resolves, its entries joining the resolved configuration in
 * run and baseline provenance — the drift-checked identity's second
 * feed.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Covariates {

    /** The service name the feed belongs to. */
    String value();
}
