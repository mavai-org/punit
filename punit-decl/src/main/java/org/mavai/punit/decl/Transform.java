package org.mavai.punit.decl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registers a method of the bindings class as a named transformation —
 * a view a contract file's {@code transforms:} block can name beside
 * the stock {@code json}/{@code xml}/{@code yaml} ones. One response
 * in, the view's value out ({@code String → T}, or
 * {@code String → Outcome<T>} where an anticipated parse failure
 * travels as a failed trial with the transform-failure reason). The
 * view is computed at most once per response and shared by every
 * consumer; {@code raw} is reserved.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Transform {

    /** The transformation's registry name — unique, never {@code raw}. */
    String value();
}
