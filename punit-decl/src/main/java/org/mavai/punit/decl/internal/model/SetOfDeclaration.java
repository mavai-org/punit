package org.mavai.punit.decl.internal.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@code set-of:} operand, normalised at parse: the member lists
 * deduplicated under membership semantics (a set is a set), the
 * {@code min-present:} floor resolved to a distinct-member count, and
 * {@code refuse-extras:} defaulted. Every contradiction, unsatisfiable
 * or saturated floor, and sharper-form spelling has already been
 * refused — the compiler consumes this record without re-validating.
 *
 * @param required members that must all appear in the selection
 * @param optional members that may appear
 * @param minPresent how many distinct optional members must appear
 * @param refuseExtras whether an unlisted selected element fails the check
 */
public record SetOfDeclaration(
        List<Object> required,
        List<Object> optional,
        int minPresent,
        boolean refuseExtras) {

    public SetOfDeclaration {
        // Not List.copyOf: null is a legal member (the family's JSON-value
        // rule admits it), and copyOf rejects null elements.
        required = Collections.unmodifiableList(new ArrayList<>(required));
        optional = Collections.unmodifiableList(new ArrayList<>(optional));
    }
}
