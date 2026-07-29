package org.mavai.punit.decl.internal.model;

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
 *     the untransformed response; {@code null} only transiently inside
 *     the parser — a {@code path:}-bearing form that omitted {@code in:}
 *     awaits the path-conditional default resolution against its owning
 *     criterion (single {@code parses:} view, else the sole declared
 *     transform, else a load refusal). The parser resolves every subject
 *     before a declaration leaves it; nothing downstream sees {@code null}.
 * @param path the selection expression, or {@code null} when the form
 *     judges its subject whole
 */
public record FormDeclaration(
        PostconditionForm form, Object argument, String view, String path, boolean optional) {

    /** A required form — the default; every check is non-negotiable until marked. */
    public FormDeclaration(PostconditionForm form, Object argument, String view, String path) {
        this(form, argument, view, path, false);
    }

    /** The reserved name of the untransformed response. */
    public static final String RAW_VIEW = "raw";

    /** This form with its defaulted subject resolved to the given view. */
    public FormDeclaration withView(String resolved) {
        return new FormDeclaration(form, argument, resolved, path, optional);
    }
}
