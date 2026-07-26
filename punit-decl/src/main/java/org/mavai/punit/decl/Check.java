package org.mavai.punit.decl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registers a method of the bindings class as a named check — the code
 * a contract file's {@code satisfies:} form resolves to, and the
 * gentlest graduation step: one predicate in code, everything else
 * still declarative. The method receives the subject (the named view's
 * value when the form carries {@code in:}, else the raw response text)
 * and answers with a {@code boolean} or an
 * {@link org.mavai.outcome.Outcome} (whose failure carries the reason
 * into the run's failure accounting).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Check {

    /** The check's registry name — unique within the check registry. */
    String value();
}
