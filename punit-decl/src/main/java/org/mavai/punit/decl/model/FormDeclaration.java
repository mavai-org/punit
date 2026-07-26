package org.mavai.punit.decl.model;

/**
 * One parsed postcondition form: the form, its argument, the subject
 * view named by its {@code in:} qualifier (the reserved primal view
 * {@code raw} when unqualified), and the optional {@code path:}
 * selection expression over a declared view's structured value.
 *
 * @param form the postcondition form
 * @param argument the form's argument — a string, or a list of strings
 *     for {@code one-of}
 * @param view the subject view; {@link #RAW_VIEW} when the form judges
 *     the untransformed response
 * @param path the selection expression, or {@code null} when the form
 *     judges its subject whole
 */
public record FormDeclaration(Form form, Object argument, String view, String path) {

    /** The reserved name of the untransformed response. */
    public static final String RAW_VIEW = "raw";
}
