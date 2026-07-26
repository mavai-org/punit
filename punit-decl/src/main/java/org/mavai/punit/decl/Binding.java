package org.mavai.punit.decl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registers a method of the bindings class as a service binding: the
 * named piece of host code a contract file's {@code service:} key
 * resolves to. One input in, one response out; the family's
 * expected-failure-versus-defect discipline applies — an anticipated
 * bad response returns as the response for the criteria to judge, and
 * only genuine defects throw (aborting the run).
 *
 * <p>An input declared as a flat list of scalars is splatted across the
 * method's parameters; any other input shape arrives as the single
 * parameter's value.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Binding {

    /** The binding's registry name — unique within the bindings class. */
    String value();
}
