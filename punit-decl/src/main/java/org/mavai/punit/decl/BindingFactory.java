package org.mavai.punit.decl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registers a method of the bindings class as a configurable service
 * <em>type</em>: the named implementation a {@code mavai-services/1}
 * definition's {@code type:} key resolves to. The method's parameter
 * list <strong>is</strong> the configuration schema — kebab-case file
 * keys map to camelCase parameters, scalar types are checked, and a
 * definition that does not fit the signature is refused at load with
 * the signature in the message. The factory runs at contract-load time
 * (cheap, side-effect-light) and returns the per-sample callable, a
 * {@link java.util.function.Function} from the input to the response.
 *
 * <p>Type names and service names are separate namespaces; registering
 * a factory under a built-in type's name is refused at load.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BindingFactory {

    /** The service-type name — unique within the type registry. */
    String value();
}
