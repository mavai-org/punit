package org.mavai.punit.decl.model;

import java.util.List;

/**
 * One input's own expectations: the input's structural position in the
 * full input list (entries without expectations occupy positions too),
 * its value, and its postcondition forms — the same conjunction, the
 * same single Bernoulli stream, made input-dependent.
 *
 * @param inputIndex the input's position in the full input list
 * @param input the input value
 * @param forms the input-specific postcondition forms
 */
public record InputExpectation(int inputIndex, Object input, List<FormDeclaration> forms) {

    public InputExpectation {
        forms = List.copyOf(forms);
    }
}
